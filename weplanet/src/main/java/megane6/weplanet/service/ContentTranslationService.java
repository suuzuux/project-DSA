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

/**
 * 운영자가 입력한 글(메인 배너·공지)의 AI 번역. 제목과 본문을 함께 번역하고, 메인 배너는 여러 장을 Gemini 호출 한 번에 번역한다
 * (무료 한도가 분당 5회·하루 20회라 배너마다 따로 부르면 금방 막힌다).
 * 같은 글·같은 언어는 서버 메모리에 기억해 두고 다시 번역하지 않는다 (글이 바뀌면 다른 글로 보고 새로 번역).
 * 실패하면 empty - 호출부가 원문을 보여주거나 안내 문구를 띄운다 (AI 실패 문구가 제목 자리에 들어가지 않게).
 * 메인 배너 번역이 통째로 실패하면 RETRY_DELAY 뒤 한 번 더 시도하고, 그래도 실패하면 RETRY_AFTER 동안은 다시 부르지 않는다
 * (AI 한도 초과·장애 때 화면을 열 때마다 기다리지 않게).
 */
@Slf4j
@Service
public class ContentTranslationService {

	private static final int MAX_REMEMBERED = 500;
	// 분당 한도가 1분마다 풀리므로 그 뒤에 다시 시도한다
	private static final Duration RETRY_AFTER = Duration.ofMinutes(1);
	// 메인 배너 번역이 통째로 실패했을 때 한 번 더 시도하기 전 쉬는 시간
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

	// 이번 묶음에서 새로 번역할 글 하나 (끝나면 future 로 결과를 알린다)
	private record Pending(String key, Source source, CompletableFuture<Translation> future) {}

	// 지금 번역해서 돌려준다 (공지 "번역보기" 버튼). 기억해 둔 번역이 있으면 바로 돌려준다
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

	// 공지 "번역보기" 응답 - 화면 JS 가 읽는 모양: {success:true, title, content} / {success:false, message}
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

	// 여러 글을 한 번에 번역하되 최대 maxWait 까지만 기다린다 (메인 배너 - 화면이 AI 응답을 오래 기다리지 않게).
	// 그 안에 끝나지 않은 번역은 empty 로 돌려주고 뒤에서 마저 끝내 기억해 둔다 (다음 방문부터 번역문이 보인다)
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
			// 늦거나 실패한 번역은 원문으로 보여준다
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		return keys.stream().map(key -> Optional.ofNullable(futures.get(key).getNow(null))).toList();
	}

	// 기억해 둔 번역 → 최근 실패(잠시 쉼) → 다른 요청이 번역 중이면 그것을 같이 기다림 → 아니면 이번 묶음에 넣는다
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
						// Gemini 가 잠깐 몰려서(503 high demand) 통째로 실패하는 경우가 많아 한 번만 더 시도한다
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

	// 글 여러 개를 JSON 배열 하나로 보내 같은 순서의 배열로 받는다. 항목마다 실패하면 그 자리는 null (전체 실패도 모두 null)
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
