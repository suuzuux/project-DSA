package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.*;
import megane6.weplanet.domain.entity.enumfolder.ReportReason;
import megane6.weplanet.domain.entity.enumfolder.ReportStatus;
import megane6.weplanet.domain.entity.enumfolder.UserStatus;
import megane6.weplanet.repository.CommentReportRepository;
import megane6.weplanet.repository.ReportRepository;
import megane6.weplanet.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 관리자 "통합 신고·제재" 화면 전용 서비스.
 * <p>
 * 신고에는 별도의 "처리 상태" 컬럼이 없다 - 신고 기록(Report/CommentReport)은 그 자체로 처리 대기 목록이고,
 * 관리자가 처리(기각 또는 대상 삭제)하면 그 신고 로우 자체가 사라지는 방식이다.
 * 그래서 목록에 남아있는 것 = 아직 처리 안 된 신고, 라는 규칙만 지키면 별도 상태값 없이도 동작한다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AdminReportService {

	private final ReportRepository reportRepository;
	private final CommentReportRepository commentReportRepository;
	private final UserRepository userRepository;
	private final PostService postService;
	private final CommentService commentService;

	// 대상 종류 - 게시글 신고인지 댓글 신고인지
	public enum TargetType {
		POST, COMMENT
	}

	// 화면에 뿌리기 위해 게시글 신고/댓글 신고를 한 형태로 합친 값
	public record ReportItem(
			TargetType targetType,
			Long targetId,				// postId 또는 commendId - 기각/삭제 액션에 사용
			ReportStatus status,
			long reportCount,			// 이 대상에 대해 처리 대기 중인 신고 건수
			ReportReason latestReason,	// 가장 최근 신고의 사유
			String latestReporterNickname,
			Long authorId,
			String authorNickname,
			String targetTitle,
			String targetExcerpt,
			LocalDateTime latestReportedAt
	) {
	}
	
	// 목록 하나를 페이지 단위로 잘라서 돌려주기 위한 공용 껍데기
	public record PageResult<T>(List<T> content, int page, int size, long totalElements) {
		public int totalPages() {
			return size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
		}
		public boolean hasPrevious() {
			return page > 0;
		}
		public boolean hasNext() {
			return (page + 1) < totalPages();
		}
	}
	
	@Transactional(readOnly = true)
	public PageResult<ReportItem> listAll(
			TargetType typeFilter,
			ReportStatus statusFilter,
			ReportReason reasonFilter,
			String keyword,
			int page,
			int size
	) {
		String trimmedKeyword = (keyword == null || keyword.isBlank())
				? null : keyword.strip();
		List<ReportItem> items = new ArrayList<>();
		
		if (typeFilter != TargetType.COMMENT) {
			Map<Long, List<Report>> byPost = reportRepository
					.search(statusFilter, reasonFilter, trimmedKeyword)
					.stream()
					.collect(Collectors.groupingBy(r -> r.getPost().getId()));
			
			for (List<Report> group : byPost.values()) {
				Report latest = group.get(0);
				Post post = latest.getPost();
				items.add(new ReportItem(
						TargetType.POST,
						post.getId(),
						latest.getStatus(),
						group.size(),
						latest.getReason(),
						latest.getReporter().getNickname(),
						post.getAuthor().getId(),
						post.getAuthor().getNickname(),
						post.getTitle(),
						excerpt(post.getContent()),
						latest.getCreatedAt()
				));
			}
		}
		
		if (typeFilter != TargetType.POST) {
			Map<Long, List<CommentReport>> byComment = commentReportRepository
					.search(statusFilter, reasonFilter, trimmedKeyword)
					.stream()
					.collect(Collectors.groupingBy(r -> r.getComment().getId()));
			
			for (List<CommentReport> group : byComment.values()) {
				CommentReport latest = group.get(0);
				Comment comment = latest.getComment();
				items.add(new ReportItem(
						TargetType.COMMENT,
						comment.getId(),
						latest.getStatus(),
						group.size(),
						latest.getReason(),
						latest.getReporter().getNickname(),
						comment.getAuthor().getId(),
						comment.getAuthor().getNickname(),
						"댓글 (원글 : " + comment.getPost().getTitle() + ")",
						excerpt(comment.getContent()),
						latest.getCreatedAt()
				));
			}
		}
		
		items.sort(Comparator.comparing(ReportItem::latestReportedAt).reversed());
		
		int fromIndex = Math.min(page * size, items.size());
		int toIndex = Math.min(fromIndex + size, items.size());
		List<ReportItem> pageContent = items.subList(fromIndex, toIndex);
		
		return new PageResult<>(pageContent, page, size, items.size());
	}

	// 목록/카드에 너무 긴 본문이 그대로 노출되지 않도록 앞부분만 자름
	private String excerpt(String content) {
		if (content == null) {
			return "";
		}
		String trimmed = content.strip();
		return trimmed.length() > 80 ? trimmed.substring(0, 80) + "…" : trimmed;
	}
	
	// 신고 기각 - 이 게시글에 걸린 처리 대기 신고를 전부 DISMISSED로 바꾼다
	public void dismissPostReports(Long postId) {
		List<Report> pending = reportRepository.findByPost_IdAndStatus(postId, ReportStatus.PENDING);
		if (pending.isEmpty()) {
			throw new IllegalArgumentException("처리 대기 중인 신고를 찾을 수 없습니다. postId=" + postId);
		}
		LocalDateTime now = LocalDateTime.now();
		for (Report report : pending) {
			report.setStatus(ReportStatus.DISMISSED);
			report.setResolvedAt(now);
		}
	}
	
	public void dismissCommentReports(Long commentId) {
		List<CommentReport> pending = commentReportRepository.findByComment_IdAndStatus(commentId, ReportStatus.PENDING);
		if (pending.isEmpty()) {
			throw new IllegalArgumentException("처리 대기 중인 신고를 찾을 수 없습니다. commentId=" + commentId);
		}
		LocalDateTime now = LocalDateTime.now();
		for (CommentReport report : pending) {
			report.setStatus(ReportStatus.DISMISSED);
			report.setResolvedAt(now);
		}
	}
	
	// 신고된 게시글 자체를 삭제 - PostService.deletePost가 그 글에 걸린 신고 기록까지 함께 지워줌
	public void deleteReportedPost(Long postId, User admin) {
		postService.deletePost(postService.getPost(postId), admin);
	}
	
	// 신고된 댓글 자체를 삭제 - CommentService.deleteComment가 그 댓글에 걸린 신고 기록까지 함께 지워줌
	public void deleteReportedComment(Long commentId, User admin) {
		commentService.deleteComment(commentId, admin);
	}

	// 작성자 계정 정지 (제재) - 신고 처리와는 별개로, 필요하면 신고를 남긴 채로도 제재만 먼저 할 수 있음
	public void suspendUser(Long userId) {
		User target = userRepository.findById(userId)
				.orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다. id=" + userId));
		target.suspend();
	}
	
	// 제재 대상 목록 - 현재 정지 상태인 회원 전체
	@Transactional(readOnly = true)
	public List<User> listSuspendedUsers() {
		return userRepository.findByStatus(UserStatus.SUSPENDED);
	}
	
	// 정지 해제 - 다시 로그인 가능한 상태로 도디ㅗㄹ림
	public void reinstateUser(Long userId) {
		User target = userRepository.findById(userId).orElseThrow(() ->
				new IllegalArgumentException("회원을 찾을 수 없습니다. id=" + userId));
		target.reinstate();
	}
}
