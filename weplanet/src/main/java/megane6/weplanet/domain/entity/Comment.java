package megane6.weplanet.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

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

    // 대댓글이면 부모 댓글, 일반 댓글이면 null (1단계까지만 허용 - 답글의 답글은 못 달게 막아둠)
    // 자기 자신을 참조하는 관계라 @Data가 만들어주는 toString()/equals()에서 제외해야 함
    // (제외하지 않으면 로그 한 줄 찍을 때 부모를 계속 따라가며 조회가 줄줄이 일어남)
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

    // 답글이 달린 원댓글을 삭제했을 때만 값이 채워짐 (null이면 정상 댓글).
    // 답글을 살려두기 위해 행 자체는 남기고 "삭제된 댓글입니다"로만 보여준다.
    private LocalDateTime deletedAt;

    // 삭제 표시만 된 댓글인지 (템플릿/서비스에서 조건 검사용)
    public boolean isDeleted() {
        return deletedAt != null;
    }

    // 저장되기 직전에 자동으로 현재 시각을 채워 넣음
    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
