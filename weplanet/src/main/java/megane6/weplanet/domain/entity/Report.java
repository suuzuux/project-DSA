package megane6.weplanet.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import megane6.weplanet.domain.entity.enumfolder.ReportReason;

import java.time.LocalDateTime;

/**
 * 게시글 신고 기록 하나. Like 엔티티랑 똑같은 원리로
 * (post_id, reporter_id) 조합에 유니크 제약을 걸어서, 같은 사람이 같은 글을 중복 신고 못 하게 막음.
 */
@Entity
@Table(
        name = "report",
        uniqueConstraints = @UniqueConstraint(columnNames = {"post_id", "reporter_id"})
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    // 신고한 사람
    @ManyToOne
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    // 신고 사유 (스팸/욕설/음란물/기타) - ReportReason enum 참고
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportReason reason;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
