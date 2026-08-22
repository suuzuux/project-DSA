package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.Post;
import megane6.weplanet.domain.entity.Report;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.ReportReason;
import megane6.weplanet.repository.ReportRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;

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
}
