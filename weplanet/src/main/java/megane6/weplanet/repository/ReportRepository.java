package megane6.weplanet.repository;

import megane6.weplanet.domain.entity.Post;
import megane6.weplanet.domain.entity.Report;
import megane6.weplanet.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReportRepository extends JpaRepository<Report, Long> {

    // 특정 유저가 특정 게시글을 이미 신고했는지 확인
    Optional<Report> findByPostAndReporter(Post post, User reporter);

    // 게시글 삭제 시 그 게시글에 달린 신고를 먼저 지우기 위함
    void deleteByPost(Post post);

    List<Report> findByPost_ArtistOrderByCreatedAtDesc(User artist);

    long countByPost_Artist(User artist);
}
