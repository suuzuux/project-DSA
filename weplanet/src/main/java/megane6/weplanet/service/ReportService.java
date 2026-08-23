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
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final CommentReportRepository commentReportRepository;

    // 게시글 신고 - 같은 사람이 같은 글을 두 번 신고하면 예외
    public void reportPost(Post post, User reporter, ReportReason reason) {
        Optional<Report> existing = reportRepository.findByPostAndReporter(post, reporter);

        if (existing.isPresent()) {
            throw new IllegalStateException("이미 신고한 게시글입니다.");
        }

        Report report = Report.builder()
                .post(post)
                .reporter(reporter)
                .reason(reason)
                .build();

        reportRepository.save(report);
    }

    // 댓글 신고 - 게시글 신고와 완전히 같은 규칙 (중복 신고 차단)
    public void reportComment(Comment comment, User reporter, ReportReason reason) {
        Optional<CommentReport> existing = commentReportRepository.findByCommentAndReporter(comment, reporter);

        if (existing.isPresent()) {
            throw new IllegalStateException("이미 신고한 댓글입니다.");
        }

        CommentReport commentReport = CommentReport.builder()
                .comment(comment)
                .reporter(reporter)
                .reason(reason)
                .build();

        commentReportRepository.save(commentReport);
    }
}
