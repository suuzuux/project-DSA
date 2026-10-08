package megane6.weplanet.service.live;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.repository.main.UserRepository;
import megane6.weplanet.service.chat.AiFanChatService;
import megane6.weplanet.service.chat.AiFanPersona;
import megane6.weplanet.service.main.GeminiClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** 라이브 중 아티스트 발화를 모아 AI 팬 댓글을 단다 (COOLDOWN 간격으로만 호출). */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiLiveCommentService {

	private static final long COOLDOWN_MS = 2_000L;
	private static final long MIN_DELAY_MS = 0L;
	private static final int MAX_SPEECH_CHARS = 600;
	private static final int MAX_CONTEXT_CHARS = 300;
	private static final int MAX_FANS_PER_ROUND = 2;

	private final GeminiClient geminiClient;
	private final AiFanChatService aiFanChatService;
	private final UserRepository userRepository;
	private final LiveBroadcastService liveBroadcastService;
	private final LiveRealtimePublisher liveRealtimePublisher;

	private final Map<Long, SpeechBuffer> buffers = new ConcurrentHashMap<>();
	private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, namedThreads());

	private static final class SpeechBuffer {
		private final StringBuilder pending = new StringBuilder();
		private String previous = "";
		private long lastFlushAt = 0L;
		private boolean scheduled = false;
	}

	public void onArtistSpeech(Long artistId, String text) {
		if (artistId == null || text == null || text.isBlank()) {
			return;
		}
		notifyHost(artistId, "말을 받았습니다. AI 댓글 작성 중...");
		SpeechBuffer buffer = buffers.computeIfAbsent(artistId, id -> new SpeechBuffer());
		synchronized (buffer) {
			if (buffer.pending.length() < MAX_SPEECH_CHARS) {
				if (!buffer.pending.isEmpty()) {
					buffer.pending.append(' ');
				}
				buffer.pending.append(text.strip());
			}
			if (buffer.scheduled) {
				return;
			}
			buffer.scheduled = true;
			long elapsed = System.currentTimeMillis() - buffer.lastFlushAt;
			long delay = Math.max(MIN_DELAY_MS, COOLDOWN_MS - elapsed);
			scheduler.schedule(() -> flush(artistId), delay, TimeUnit.MILLISECONDS);
		}
	}

	private void flush(Long artistId) {
		SpeechBuffer buffer = buffers.get(artistId);
		if (buffer == null) {
			return;
		}
		String speech;
		String previous;
		synchronized (buffer) {
			speech = buffer.pending.toString().strip();
			buffer.pending.setLength(0);
			buffer.scheduled = false;
			buffer.lastFlushAt = System.currentTimeMillis();
			previous = buffer.previous;
			buffer.previous = tail(speech, MAX_CONTEXT_CHARS);
		}
		if (speech.isEmpty()) {
			return;
		}
		try {
			if (liveBroadcastService.findLive(artistId).isEmpty()) {
				buffers.remove(artistId);
				return;
			}
			User artist = userRepository.findById(artistId).orElse(null);
			if (artist == null) {
				return;
			}
			Map<String, User> fansByUsername = new HashMap<>();
			for (User fan : userRepository.findByUsernameIn(AiFanPersona.usernames())) {
				fansByUsername.put(fan.getUsername(), fan);
			}
			List<AiFanPersona> personas = pickPersonas(fansByUsername);
			if (personas.isEmpty()) {
				notifyHost(artistId, "AI 팬 계정(aifan_mina 등)이 DB에 없어서 댓글을 달 수 없습니다.");
				return;
			}

			String raw = geminiClient.generateLiveJson(buildPrompt(artist.getNickname(), speech, previous, personas));
			List<String> replies = aiFanChatService.parseReplies(raw);
			log.info("라이브 AI 댓글: artistId={} 인식된 말=\"{}\" AI 팬 계정 {}명, 생성된 댓글 {}개",
					artistId, speech, personas.size(), replies.size());
			if (raw == null || raw.isBlank()) {
				String reason = geminiClient.lastFailure();
				notifyHost(artistId, reason.isBlank() ? "Gemini 호출에 실패했습니다." : "Gemini 호출 실패: " + reason);
				return;
			}
			if (replies.isEmpty()) {
				notifyHost(artistId, "AI 응답을 댓글로 읽지 못했습니다.");
				return;
			}

			int posted = 0;
			for (int i = 0; i < personas.size() && i < replies.size(); i++) {
				User fan = fansByUsername.get(personas.get(i).username());
				String content = replies.get(i);
				if (content == null || content.isBlank()) {
					continue;
				}
				if (post(fan, artistId, content)) {
					posted++;
				}
			}
			if (posted == 0) {
				notifyHost(artistId, "AI 댓글 저장에 실패했습니다.");
			}
		} catch (RuntimeException e) {
			log.warn("라이브 AI 댓글 생성 실패 (artistId={}): {}", artistId, e.getMessage());
			notifyHost(artistId, "AI 댓글 실패: " + e.getMessage());
		}
	}

	private void notifyHost(Long artistId, String message) {
		Map<String, Object> payload = new HashMap<>();
		payload.put("type", "ai");
		payload.put("message", message);
		liveRealtimePublisher.notifyHost(artistId, payload);
	}

	private boolean post(User fan, Long artistId, String content) {
		try {
			return liveBroadcastService.addAiFanComment(fan, artistId, content)
					.map(saved -> {
						liveRealtimePublisher.publishComment(artistId, saved);
						return true;
					})
					.orElse(false);
		} catch (RuntimeException e) {
			log.warn("라이브 AI 댓글 저장/전송 실패 ({}): {}", fan.getUsername(), e.getMessage());
			return false;
		}
	}

	private static List<AiFanPersona> pickPersonas(Map<String, User> fansByUsername) {
		List<AiFanPersona> available = new ArrayList<>();
		for (AiFanPersona persona : AiFanPersona.ALL) {
			if (fansByUsername.containsKey(persona.username())) {
				available.add(persona);
			}
		}
		Collections.shuffle(available);
		int count = Math.min(1 + ThreadLocalRandom.current().nextInt(MAX_FANS_PER_ROUND), available.size());
		return available.subList(0, count);
	}

	private static String buildPrompt(String artistNickname, String speech, String previous, List<AiFanPersona> personas) {
		StringBuilder sb = new StringBuilder();
		sb.append("아티스트 \"").append(artistNickname).append("\"가 라이브에서 방금 한 말에 팬이 채팅으로 대답한다.\n");
		sb.append("방금 한 말: \"").append(speech).append("\"\n");
		if (previous != null && !previous.isBlank()) {
			sb.append("직전 맥락(참고만, 대답의 주제는 방금 한 말이다): \"").append(previous).append("\"\n");
		}
		sb.append("각 팬은 이 말에 대한 대답을 한 문장만 써라.\n");
		sb.append("- 질문이면 그 질문에 답해라.\n");
		sb.append("- 질문이 아니면 그 말에 나온 내용만 집어서 받아라.\n");
		sb.append("- 금지: 사랑해, 응원해, 최고야, 화이팅, 심쿵처럼 어떤 말에도 붙일 수 있는 멘트. 아티스트가 하지 않은 이야기를 꺼내지 마라.\n");
		sb.append("- 아티스트가 말한 언어와 같은 언어. 80자 이내. 팬마다 문장을 다르게.\n");
		sb.append("말투만 아래를 따른다. 말투 때문에 주제를 바꾸지 마라.\n");
		for (int i = 0; i < personas.size(); i++) {
			AiFanPersona p = personas.get(i);
			sb.append(i + 1).append(". ").append(p.nickname()).append(" — ").append(p.personality()).append('\n');
		}
		sb.append("위 순서대로 JSON 배열만 출력해. 키는 nickname, content 이다. 닉네임은 위와 동일해야 한다.");
		return sb.toString();
	}

	private static String tail(String text, int maxChars) {
		return text.length() <= maxChars ? text : text.substring(text.length() - maxChars);
	}

	private static ThreadFactory namedThreads() {
		AtomicInteger seq = new AtomicInteger();
		return runnable -> {
			Thread thread = new Thread(runnable, "ai-live-comment-" + seq.incrementAndGet());
			thread.setDaemon(true);
			return thread;
		};
	}

	@PreDestroy
	void shutdown() {
		scheduler.shutdownNow();
	}
}
