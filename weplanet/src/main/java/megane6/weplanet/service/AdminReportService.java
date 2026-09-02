package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.Comment;
import megane6.weplanet.domain.entity.CommentReport;
import megane6.weplanet.domain.entity.Post;
import megane6.weplanet.domain.entity.Report;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.ReportReason;
import megane6.weplanet.repository.CommentReportRepository;
import megane6.weplanet.repository.ReportRepository;
import megane6.weplanet.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
			Long reportId,
			TargetType targetType,
			ReportReason reason,
			String reporterNickname,
			Long authorId,
			String authorNickname,
			String targetTitle,
			String targetExcerpt,
			LocalDateTime createdAt
	) {
	}

	@Transactional(readOnly = true)
	public List<ReportItem> listAll() {
		List<ReportItem> items = new ArrayList<>();

		for (Report report : reportRepository.findAllByOrderByCreatedAtDesc()) {
			Post post = report.getPost();
			items.add(new ReportItem(
					report.getId(),
					TargetType.POST,
					report.getReason(),
					report.getReporter().getNickname(),
					post.getAuthor().getId(),
					post.getAuthor().getNickname(),
					post.getTitle(),
					excerpt(post.getContent()),
					report.getCreatedAt()
			));
		}

		for (CommentReport report : commentReportRepository.findAllByOrderByCreatedAtDesc()) {
			Comment comment = report.getComment();
			items.add(new ReportItem(
					report.getId(),
					TargetType.COMMENT,
					report.getReason(),
					report.getReporter().getNickname(),
					comment.getAuthor().getId(),
					comment.getAuthor().getNickname(),
					"댓글 (원글: " + comment.getPost().getTitle() + ")",
					excerpt(comment.getContent()),
					report.getCreatedAt()
			));
		}

		items.sort(Comparator.comparing(ReportItem::createdAt).reversed());
		return items;
	}

	// 목록/카드에 너무 긴 본문이 그대로 노출되지 않도록 앞부분만 자름
	private String excerpt(String content) {
		if (content == null) {
			return "";
		}
		String trimmed = content.strip();
		return trimmed.length() > 80 ? trimmed.substring(0, 80) + "…" : trimmed;
	}

	// 신고 기각 - 신고 기록만 지우고 게시글/댓글은 그대로 둔다
	public void dismissPostReport(Long reportId) {
		if (!reportRepository.existsById(reportId)) {
			throw new IllegalArgumentException("신고 내역을 찾을 수 없습니다. id=" + reportId);
		}
		reportRepository.deleteById(reportId);
	}

	public void dismissCommentReport(Long reportId) {
		if (!commentReportRepository.existsById(reportId)) {
			throw new IllegalArgumentException("신고 내역을 찾을 수 없습니다. id=" + reportId);
		}
		commentReportRepository.deleteById(reportId);
	}

	// 신고된 게시글 자체를 삭제 - PostService.deletePost가 이 신고 기록까지 함께 지워줌
	public void deleteReportedPost(Long reportId, User admin) {
		Report report = reportRepository.findById(reportId)
				.orElseThrow(() -> new IllegalArgumentException("신고 내역을 찾을 수 없습니다. id=" + reportId));
		postService.deletePost(report.getPost(), admin);
	}

	// 신고된 댓글 자체를 삭제 - CommentService.deleteComment가 이 신고 기록까지 함께 지워줌
	public void deleteReportedComment(Long reportId, User admin) {
		CommentReport report = commentReportRepository.findById(reportId)
				.orElseThrow(() -> new IllegalArgumentException("신고 내역을 찾을 수 없습니다. id=" + reportId));
		commentService.deleteComment(report.getComment().getId(), admin);
	}

	// 작성자 계정 정지 (제재) - 신고 처리와는 별개로, 필요하면 신고를 남긴 채로도 제재만 먼저 할 수 있음
	public void suspendUser(Long userId) {
		User target = userRepository.findById(userId)
				.orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다. id=" + userId));
		target.suspend();
	}
}
