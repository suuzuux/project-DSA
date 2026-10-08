package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.*;
import megane6.weplanet.domain.entity.enumfolder.*;
import megane6.weplanet.repository.CommentReportRepository;
import megane6.weplanet.repository.ReportRepository;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.service.admin.AdminActionLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 관리자 통합 신고·제재 서비스 (처리 상태 컬럼 없이 남은 신고 = 처리 대기). */
@Service
@RequiredArgsConstructor
@Transactional
public class AdminReportService {

	private final ReportRepository reportRepository;
	private final CommentReportRepository commentReportRepository;
	private final UserRepository userRepository;
	
	private final PostService postService;
	private final CommentService commentService;
	private final AdminActionLogService als;
	private final AdminUserService aus;
	// 댓글 신고 대상 제목을 요청 로케일로 만든다.
	private final megane6.weplanet.i18n.Messages messages;

	// 대상 종류 (게시글 / 댓글)
	public enum TargetType {
		POST, COMMENT
	}

	// 게시글·댓글 신고를 한 형태로 합친 목록 항목
	public record ReportItem(
			TargetType targetType,
			Long targetId,				// postId 또는 commentId
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
	
	// 페이지 단위 목록 결과
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
						messages.get("admin.reports.commentTarget", comment.getPost().getTitle()),
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

	// 긴 본문은 앞부분만 자른다.
	private String excerpt(String content) {
		if (content == null) {
			return "";
		}
		String trimmed = content.strip();
		return trimmed.length() > 80 ? trimmed.substring(0, 80) + "…" : trimmed;
	}
	
	// 신고 기각 - 처리 대기 신고를 DISMISSED 로 바꾼다.
	public void dismissPostReports(
			Long postId,
			Long adminId,
			String ipAddress) {
		List<Report> pending = reportRepository
				.findByPost_IdAndStatus(postId, ReportStatus.PENDING);
		if (pending.isEmpty()) {
			throw new IllegalArgumentException("admin.error.report.pendingNotFound");
		}
		LocalDateTime now = LocalDateTime.now();
		for (Report report : pending) {
			report.setStatus(ReportStatus.DISMISSED);
			report.setResolvedAt(now);
		}
		als.recordAction(
				adminId,
				AdminActionType.REPORT_DISMISS,
				AdminTargetType.REPORT,
				postId,
				"게시글 신고 " + pending.size() + "건 기각",
				ipAddress);
	}
	
	public void dismissCommentReports(
			Long commentId,
			Long adminId,
			String ipAddress) {
		List<CommentReport> pending = commentReportRepository
				.findByComment_IdAndStatus(commentId, ReportStatus.PENDING);
		if (pending.isEmpty()) {
			throw new IllegalArgumentException("admin.error.report.pendingNotFound");
		}
		LocalDateTime now = LocalDateTime.now();
		for (CommentReport report : pending) {
			report.setStatus(ReportStatus.DISMISSED);
			report.setResolvedAt(now);
		}
		als.recordAction(
				adminId,
				AdminActionType.REPORT_DISMISS,
				AdminTargetType.REPORT,
				commentId,
				"댓글 신고 " + pending.size() + "건 기각",
				ipAddress);
	}
	
	// 신고된 게시글 삭제 (신고 기록도 함께 삭제됨)
	public void deleteReportedPost(
			Long postId, User admin, String ipAddress) {
		Post post = postService.getPost(postId);
		String title = post.getTitle();
		
		postService.deletePost(post, admin);
		
		als.recordAction(
				admin.getId(),
				AdminActionType.REPORT_RESOLVE,
				AdminTargetType.REPORT,
				postId,
				"게시글 신고 처리 : 콘텐츠 삭제 (" + title + ")",
				ipAddress);
	}
	
	
	// 신고된 댓글 삭제 (신고 기록도 함께 삭제됨)
	public void deleteReportedComment(
			Long commentId, User admin, String ipAddress) {
		Comment comment = commentService.getComment(commentId);
		String content = excerpt(comment.getContent());
		
		commentService.deleteComment(commentId, admin);
		als.recordAction(
				admin.getId(),
				AdminActionType.REPORT_RESOLVE,
				AdminTargetType.REPORT,
				commentId,
				"댓글 신고 처리 : 콘텐츠 삭제 (" + content + ")",
				ipAddress);
	}
	
	// 작성자 계정 정지 (신고 처리와 별개로 가능)
	public void suspendUser(Long userId, Long adminId, String ipAddress) {
		aus.suspendUser(userId, adminId, ipAddress);
	}
	
	// 정지 상태 회원 목록
	@Transactional(readOnly = true)
	public List<User> listSuspendedUsers() {
		return userRepository.findByStatus(UserStatus.SUSPENDED);
	}
	
	// 정지 해제
	public void reinstateUser(Long userId, Long adminId, String ipAddress) {
		aus.reinstateUser(userId, adminId, ipAddress);
	}
}
