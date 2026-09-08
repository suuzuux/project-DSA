package megane6.weplanet.service.live;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.live.LiveCommentView;
import megane6.weplanet.domain.dto.live.LiveStatusView;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.LiveSessionStatus;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.domain.entity.live.LiveComment;
import megane6.weplanet.domain.entity.live.LiveSession;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.repository.live.LiveCommentReportRepository;
import megane6.weplanet.repository.live.LiveCommentRepository;
import megane6.weplanet.repository.live.LiveSessionRepository;
import megane6.weplanet.service.ChatFilterService;
import megane6.weplanet.service.community.CommunityJoinService;
import megane6.weplanet.service.media.BoardMediaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LiveBroadcastService {

	private static final int RECENT_COMMENT_LIMIT = 100;

	private final LiveSessionRepository liveSessionRepository;
	private final LiveCommentRepository liveCommentRepository;
	private final LiveCommentReportRepository liveCommentReportRepository;
	private final UserRepository userRepository;
	private final CommunityJoinService communityJoinService;
	private final ChatFilterService chatFilterService;
	private final BoardMediaService boardMediaService;

	@Transactional(readOnly = true)
	public Optional<LiveSession> findLive(Long artistId) {
		return liveSessionRepository.findFirstByArtist_IdAndStatus(artistId, LiveSessionStatus.LIVE);
	}

	@Transactional(readOnly = true)
	public LiveStatusView status(Long artistId) {
		return findLive(artistId)
				.map(LiveStatusView::from)
				.orElseGet(LiveStatusView::offline);
	}

	@Transactional
	public LiveStatusView start(User host, User artist) {
		requirePortalHost(host, artist);
		Optional<LiveSession> existing = liveSessionRepository.findFirstByArtistAndStatus(artist, LiveSessionStatus.LIVE);
		if (existing.isPresent()) {
			LiveSession session = existing.get();
			if (!session.isHost(host)) {
				throw new IllegalStateException("이미 다른 호스트가 방송 중입니다.");
			}
			return LiveStatusView.from(session);
		}
		LiveSession saved = liveSessionRepository.save(LiveSession.start(artist, host));
		return LiveStatusView.from(saved);
	}

	@Transactional
	public LiveStatusView end(User actor, User artist) {
		requirePortalHost(actor, artist);
		LiveSession session = liveSessionRepository.findFirstByArtistAndStatus(artist, LiveSessionStatus.LIVE)
				.orElse(null);
		if (session == null) {
			return LiveStatusView.offline();
		}
		if (!session.isHost(actor) && actor.getRole() != Role.AGENCY && !actor.getId().equals(artist.getId())) {
			throw new IllegalStateException("방송을 종료할 권한이 없습니다.");
		}
		session.end();
		return LiveStatusView.offline();
	}

	@Transactional
	public boolean endIfLive(Long artistId) {
		Optional<LiveSession> live = liveSessionRepository.findFirstByArtist_IdAndStatus(artistId, LiveSessionStatus.LIVE);
		if (live.isEmpty()) {
			return false;
		}
		live.get().end();
		return true;
	}

	@Transactional
	public Long saveReplay(User host, User artist, MultipartFile file) {
		requirePortalHost(host, artist);
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("다시보기 영상이 없습니다.");
		}
		String title = BoardMediaService.LIVE_REPLAY_TITLE_PREFIX + " · "
				+ LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm"));
		return boardMediaService.create(
				artist.getId(),
				host.getId(),
				title,
				"라이브 방송 다시보기",
				List.of(file));
	}

	@Transactional(readOnly = true)
	public List<LiveCommentView> comments(User viewer, Long artistId) {
		requireCanWatch(viewer, artistId);
		return findLive(artistId)
				.map(session -> {
					List<LiveComment> comments = liveCommentRepository.findBySessionOrderByCreatedAtAsc(session);
					int from = Math.max(0, comments.size() - RECENT_COMMENT_LIMIT);
					List<LiveComment> recent = comments.subList(from, comments.size());
					List<Long> ids = recent.stream().map(LiveComment::getId).toList();
					Set<Long> reportedIds = viewer == null || ids.isEmpty()
							? Set.of()
							: new HashSet<>(liveCommentReportRepository.findReportedCommentIds(viewer, ids));
					return recent.stream()
							.map(comment -> LiveCommentView.of(
									comment,
									communityJoinService.displayNickname(comment.getAuthor(), artistId),
									artistId,
									reportedIds.contains(comment.getId())))
							.toList();
				})
				.orElseGet(List::of);
	}

	@Transactional
	public LiveCommentView addComment(User author, Long artistId, String content) {
		requireCanWatch(author, artistId);
		if (content == null || content.isBlank()) {
			throw new IllegalArgumentException("댓글 내용을 입력해주세요.");
		}
		String trimmed = content.trim();
		if (trimmed.length() > 500) {
			throw new IllegalArgumentException("댓글은 500자 이내로 입력해주세요.");
		}
		if (chatFilterService.containsBannedWord(trimmed)) {
			throw new IllegalArgumentException("부적절한 언어가 포함되어 전송이 제한되었습니다.");
		}
		LiveSession session = findLive(artistId)
				.orElseThrow(() -> new IllegalStateException("진행 중인 라이브가 없습니다."));
		LiveComment saved = liveCommentRepository.save(LiveComment.create(session, author, trimmed));
		return LiveCommentView.of(saved, communityJoinService.displayNickname(author, artistId), artistId);
	}

	@Transactional
	public void deleteCommentForArtistCommunity(Long commentId, User artist) {
		LiveComment comment = liveCommentRepository.findById(commentId)
				.orElseThrow(() -> new IllegalArgumentException("채팅을 찾을 수 없습니다."));
		if (artist == null
				|| comment.getSession().getArtist() == null
				|| !comment.getSession().getArtist().getId().equals(artist.getId())) {
			throw new IllegalStateException("이 커뮤니티의 채팅만 삭제할 수 있습니다.");
		}
		liveCommentReportRepository.deleteByComment(comment);
		liveCommentRepository.delete(comment);
	}

	@Transactional(readOnly = true)
	public LiveComment requireCommentForArtist(Long commentId, Long artistId) {
		LiveComment comment = liveCommentRepository.findById(commentId)
				.orElseThrow(() -> new IllegalArgumentException("채팅을 찾을 수 없습니다."));
		if (comment.getSession().getArtist() == null
				|| !comment.getSession().getArtist().getId().equals(artistId)) {
			throw new IllegalArgumentException("해당 아티스트 라이브 채팅이 아닙니다.");
		}
		return comment;
	}

	@Transactional(readOnly = true)
	public LiveSession requireLive(Long artistId) {
		return findLive(artistId)
				.orElseThrow(() -> new IllegalStateException("진행 중인 라이브가 없습니다."));
	}

	@Transactional(readOnly = true)
	public void requireCanWatch(User user, Long artistId) {
		if (user == null) {
			throw new IllegalStateException("로그인이 필요합니다.");
		}
		LiveSession live = findLive(artistId).orElse(null);
		if (live != null && live.isHost(user)) {
			return;
		}
		if (user.getId().equals(artistId)) {
			return;
		}
		if (user.getRole() == Role.ADMIN) {
			return;
		}
		if (user.getRole() == Role.AGENCY) {
			User artist = userRepository.findOneById(artistId).orElse(null);
			if (artist != null
					&& user.agencyId() != null
					&& user.agencyId().equals(artist.agencyId())) {
				return;
			}
		}
		if (!communityJoinService.isJoined(user, artistId)) {
			throw new IllegalStateException("커뮤니티 가입자만 라이브를 이용할 수 있습니다.");
		}
	}

	private void requirePortalHost(User host, User artist) {
		if (host == null || artist == null) {
			throw new IllegalStateException("포털 사용자만 방송을 시작할 수 있습니다.");
		}
		if (artist.getRole() != Role.ARTIST) {
			throw new IllegalArgumentException("아티스트 계정이 아닙니다.");
		}
		if (host.getRole() == Role.ARTIST && host.getId().equals(artist.getId())) {
			return;
		}
		if (host.getRole() == Role.AGENCY) {
			return;
		}
		throw new IllegalStateException("아티스트 또는 에이전시만 방송을 시작할 수 있습니다.");
	}
}
