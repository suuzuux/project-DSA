package megane6.weplanet.service;

import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.enumfolder.Language;
import megane6.weplanet.i18n.Messages;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** 운영자 글(배너·공지) AI 번역 - 묶어서 한 번에 호출하고 결과는 메모리에 캐시, 실패 시 잠시 재시도를 멈춘다. */
@Slf4j
@Service
public class ContentTranslationService {

	private static final int MAX_REMEMBERED = 500;
	// 분당 한도가 풀리는 시간
	private static final Duration RETRY_AFTER = Duration.ofMinutes(1);
	// 묶음 번역 실패 시 재시도 전 대기
	private static final Duration RETRY_DELAY = Duration.ofSeconds(2);

	private final GeminiClient geminiClient;
	private final JsonMapper jsonMapper;
	private final Messages messages;
	private final Executor executor;
	private final Map<String, Translation> remembered = new ConcurrentHashMap<>();
	private final Map<String, CompletableFuture<Translation>> inProgress = new ConcurrentHashMap<>();
	private final Map<String, Long> failedAt = new ConcurrentHashMap<>();

	public ContentTranslationService(GeminiClient geminiClient, JsonMapper jsonMapper, Messages messages,
									 @Qualifier("contentTranslationExecutor") Executor executor) {
		this.geminiClient = geminiClient;
		this.jsonMapper = jsonMapper;
		this.messages = messages;
		this.executor = executor;
	}

	public record Source(String title, String body) {}

	public record Translation(String title, String body) {}

	// 이번 묶음에서 새로 번역할 글
	private record Pending(String key, Source source, CompletableFuture<Translation> future) {}

	// 공지 번역보기 (캐시가 있으면 바로 반환)
	public Optional<Translation> translate(String title, String body, Language target) {
		String key = key(title, body, target);
		Translation known = remembered.get(key);
		if (known != null) {
			return Optional.of(known);
		}
		Translation result = callAi(List.of(new Source(title, body)), target).get(0);
		if (result != null) {
			remember(key, result);
		}
		return Optional.ofNullable(result);
	}

	// 공지 번역 응답 {success, title, content} 또는 {success:false, message}
	public Map<String, Object> noticeResponse(String title, String content, Language target) {
		Map<String, Object> result = new LinkedHashMap<>();
		Optional<Translation> translated = translate(title, content, target);
		result.put("success", translated.isPresent());
		if (translated.isPresent()) {
			result.put("title", translated.get().title());
			result.put("content", translated.get().body());
		} else {
			result.put("message", messages.get("error.ai.unavailable"));
		}
		return result;
	}

	// 여러 글을 묶어 번역하되 maxWait 까지만 기다린다 (늦은 번역은 뒤에서 마저 캐시).
	public List<Optional<Translation>> translateAll(List<Source> sources, Language target, Duration maxWait) {
		Map<String, CompletableFuture<Translation>> futures = new LinkedHashMap<>();
		List<Pending> batch = new ArrayList<>();
		List<String> keys = new ArrayList<>(sources.size());
		for (Source source : sources) {
			String key = key(source.title(), source.body(), target);
			keys.add(key);
			if (!futures.containsKey(key)) {
				futures.put(key, lookupOrQueue(key, source, batch));
			}
		}
		if (!batch.isEmpty()) {
			startBatch(batch, target);
		}
		try {
			CompletableFuture.allOf(futures.values().toArray(CompletableFuture[]::new)).get(maxWait.toMillis(), TimeUnit.MILLISECONDS);
		} catch (TimeoutException | ExecutionException e) {
			// 늦거나 실패한 번역은 원문으로 보여준다.
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		return keys.stream().map(key -> Optional.ofNullable(futures.get(key).getNow(null))).toList();
	}

	// 캐시 → 최근 실패 → 진행 중 번역 대기 → 새 묶음 순으로 처리
	private CompletableFuture<Translation> lookupOrQueue(String key, Source source, List<Pending> batch) {
		Translation known = remembered.get(key);
		if (known != null) {
			return CompletableFuture.completedFuture(known);
		}
		if (failedRecently(key)) {
			return CompletableFuture.completedFuture(null);
		}
		CompletableFuture<Translation> created = new CompletableFuture<>();
		CompletableFuture<Translation> running = inProgress.putIfAbsent(key, created);
		if (running != null) {
			return running;
		}
		batch.add(new Pending(key, source, created));
		return created;
	}

	private void startBatch(List<Pending> batch, Language target) {
		try {
			executor.execute(() -> {
				List<Translation> results = null;
				try {
					List<Source> sources = batch.stream().map(Pending::source).toList();
					results = callAi(sources, target);
					if (results.stream().allMatch(Objects::isNull)) {
						// 503 일시 과부하에 대비해 한 번 더 시도한다.
						Thread.sleep(RETRY_DELAY.toMillis());
						results = callAi(sources, target);
					}
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				} finally {
					for (int i = 0; i < batch.size(); i++) {
						finish(batch.get(i), results == null ? null : results.get(i));
					}
				}
			});
		} catch (RejectedExecutionException e) {
			batch.forEach(pending -> finish(pending, null));
		}
	}

	private void finish(Pending pending, Translation result) {
		if (result != null) {
			remember(pending.key(), result);
			failedAt.remove(pending.key());
		} else {
			rememberFailure(pending.key());
		}
		inProgress.remove(pending.key());
		pending.future().complete(result);
	}

	// 여러 글을 JSON 배열로 보내 같은 순서로 받는다 (실패 항목은 null).
	private List<Translation> callAi(List<Source> sources, Language target) {
		List<Translation> results = new ArrayList<>();
		sources.forEach(source -> results.add(null));
		try {
			List<Map<String, String>> input = sources.stream()
					.map(source -> {
						Map<String, String> item = new LinkedHashMap<>();
						item.put("title", source.title() == null ? "" : source.title());
						item.put("body", source.body() == null ? "" : source.body());
						return item;
					})
					.toList();
			String prompt = "아래 JSON 배열의 각 항목에 있는 title 과 body 값을 자연스러운 " + target.displayNameKo() + "로 번역해서, "
					+ "같은 순서·같은 개수·같은 키(title, body)를 가진 JSON 배열 하나로만 답해줘. "
					+ "줄바꿈·마크다운 기호·이모지·URL은 그대로 둬. 사람·그룹·팬덤 이름은 영문으로 쓴 것은 그대로 두고, "
					+ "한글로 쓴 것은 " + target.displayNameKo() + "에서 쓰는 표기로 옮겨줘 (예: 일본어는 가타카나, 영어는 로마자).\n\n"
					+ jsonMapper.writeValueAsString(input);
			String json = geminiClient.generateTranslationJson(prompt);
			if (json == null || json.isBlank()) {
				return results;
			}
			JsonNode root = jsonMapper.readTree(stripCodeFence(json.strip()));
			if (!root.isArray() || root.size() != sources.size()) {
				log.warn("콘텐츠 번역 응답 개수가 다름: 보낸 {}개", sources.size());
				return results;
			}
			for (int i = 0; i < sources.size(); i++) {
				results.set(i, toTranslation(sources.get(i), root.get(i)));
			}
		} catch (RuntimeException e) {
			log.warn("콘텐츠 번역 실패: {}", e.toString());
		}
		return results;
	}

	private static Translation toTranslation(Source source, JsonNode item) {
		String title = item.path("title").asString("").strip();
		String body = item.path("body").asString("").strip();
		if (title.isEmpty() && source.title() != null && !source.title().isBlank()) {
			return null;
		}
		return new Translation(title, source.body() == null ? null : body);
	}

	private static String stripCodeFence(String text) {
		if (!text.startsWith("```")) {
			return text;
		}
		int start = text.indexOf('\n');
		int end = text.lastIndexOf("```");
		return start >= 0 && end > start ? text.substring(start + 1, end) : text;
	}

	private void remember(String key, Translation translation) {
		if (remembered.size() >= MAX_REMEMBERED) {
			remembered.clear();
		}
		remembered.put(key, translation);
	}

	private boolean failedRecently(String key) {
		Long at = failedAt.get(key);
		return at != null && System.currentTimeMillis() - at < RETRY_AFTER.toMillis();
	}

	private void rememberFailure(String key) {
		if (failedAt.size() >= MAX_REMEMBERED) {
			failedAt.clear();
		}
		failedAt.put(key, System.currentTimeMillis());
	}

	private static String key(String title, String body, Language target) {
		return target + "\u0001" + (title == null ? "" : title) + "\u0001" + (body == null ? "" : body);
	}
}
