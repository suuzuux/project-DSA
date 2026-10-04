package megane6.weplanet.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

// 사람과 사람(팬↔팬, 팬→아티스트) 사이의 팔로우 하나. communityId(그 커뮤니티 아티스트의 User.id) 커뮤니티에 속하고,
// 그 커뮤니티를 탈퇴하면 함께 지워진다. 팬→아티스트 팔로우는 following_id == community_id 다.
@Entity
@Table(name = "user_follows")
@IdClass(UserFollow.Pk.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserFollow {

    @Id
    @Column(name = "follower_id")
    private Long followerId;

    @Id
    @Column(name = "following_id")
    private Long followingId;

    @Id
    @Column(name = "community_id")
    private Long communityId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pk implements Serializable {
        private Long followerId;
        private Long followingId;
        private Long communityId;
    }
}
