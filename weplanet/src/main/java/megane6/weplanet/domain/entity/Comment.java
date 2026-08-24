package megane6.weplanet.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 댓글 하나를 표현하는 엔티티. Post와 구조가 거의 똑같고,
 * "어떤 게시글에 달린 댓글인지(post)"와 "댓글 내용(content)"만 추가로 갖고 있음.
 */
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

    // 이 댓글이 달린 게시글 (댓글 여러 개가 게시글 하나를 가리키는 다대일 관계)
    @ManyToOne
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    // 댓글 작성자
    @ManyToOne
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(nullable = false, length = 500)
    private String content;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 저장되기 직전에 자동으로 현재 시각을 채워 넣음
    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
