package megane6.weplanet.repository;

import megane6.weplanet.domain.entity.Post;
import megane6.weplanet.domain.entity.Report;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.ReportReason;
import megane6.weplanet.domain.entity.enumfolder.ReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReportRepository extends JpaRepository<Report, Long> {

    // 특정 유저가 특정 게시글을 이미 신고했는지 확인
    Optional<Report> findByPostAndReporter(Post post, User reporter);

    // 게시글 삭제 시 그 게시글에 달린 신고를 먼저 지우기 위함
    void deleteByPost(Post post);

    List<Report> findByPost_ArtistOrderByCreatedAtDesc(User artist);

    long countByPost_Artist(User artist);

    // 관리자 "통합 신고 및 제재" 목록 - 특정 상태(예 : 대기중)인 신고만 최신순으로
    @Query("""
        SELECT r FROM Report r
        WHERE r.status = :status
          AND (:reason IS NULL OR r.reason = :reason)
          AND (:keyword IS NULL OR r.post.author.nickname LIKE CONCAT('%', :keyword, '%'))
        ORDER BY r.createdAt DESC
        """)
    List<Report> search(@Param("status") ReportStatus status,
                        @Param("reason") ReportReason reason,
                        @Param("keyword") String keyword);
    
    long countByStatus(ReportStatus status);
    long countByResolvedAtAfter(LocalDateTime dateTime);
    
    List<Report> findByPost_IdAndStatus(Long postId, ReportStatus status);
}
