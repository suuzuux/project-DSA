package megane6.weplanet.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

/** 댓글 엔티티. */
@Entity
@Table(name = "comment")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 댓글이 달린 게시글
    @ManyToOne
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    // 답글이면 부모 댓글 (1단계까지만, 순환 조회를 막으려고 toString/equals 에서 제외).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Comment parent;

    // 댓글 작성자
    @ManyToOne
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(nullable = false, length = 500)
    private String content;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 답글이 있는 원댓글을 지우면 행을 남기고 삭제 시각만 채운다.
    private LocalDateTime deletedAt;

    // 삭제 표시된 댓글인지
    public boolean isDeleted() {
        return deletedAt != null;
    }

    // 저장 직전에 현재 시각을 채운다.
    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
