package megane6.weplanet.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 게시글 엔티티 (post 테이블). */
@Entity
@Table(name = "post")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 팬 게시판(FAN) / 아티스트 게시판(ARTIST)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BoardType boardType;

    // 소속 아티스트 커뮤니티
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artist_id")
    private User artist;

    @Column(nullable = false, length = 200)
    private String title;

    // 길이 제한 없는 본문 (TEXT)
    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // 작성자
    @ManyToOne
    @JoinColumn(name = "author_id")
    private User author;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 인기순 정렬용 좋아요 수 (좋아요·취소 때 갱신).
    @Builder.Default
    @Column(nullable = false)
    private int likeCount = 0;

    // Hide from Artists (팬 게시판에서만 사용)
    @Builder.Default
    @Column(name = "hidden_from_artist", nullable = false)
    private boolean hiddenFromArtist = false;

    // 첨부 링크 (선택)
    @Column(name = "link_url", length = 500)
    private String linkUrl;

    // 저장 직전에 작성 시각을 채운다.
    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    // 목록 미리보기 글 (마크다운 기호 제거, 줄바꿈 유지).
    public String getPreviewText() {
        return megane6.weplanet.util.MarkdownPreview.of(content);
    }
}
