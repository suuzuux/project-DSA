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

// FOLLOW-01 + GroupFollow 통합: 사람과 사람(팬↔팬, 팬↔아티스트 계정) 사이의 팔로우 기록 하나.
// 원래 GroupFollow(About 위젯의 팬→아티스트 그룹 팔로우, 와이어프레임 26번)와 UserFollow(팬↔팬)가
// 별개 테이블/서비스였는데, 이름을 "UserFollow" 하나로 통합했다.
//
// communityId: 이 팔로우가 "어느 커뮤니티 소속인지"를 나타낸다 (그 커뮤니티 아티스트의 User.id와 같은 값).
// 팔로우는 특정 커뮤니티에 종속된다 - 같은 두 사람이 여러 커뮤니티에 함께 가입돼 있어도, 팔로우는 그
// 관계가 맺어진 그 커뮤니티 하나에만 속한 별개의 관계로 취급한다. 그래서 한쪽이 그 커뮤니티를
// 탈퇴하면(CommunityJoinService.leave) 다른 공유 커뮤니티가 남아있어도 상관없이 이 관계는 함께 삭제된다.
// 팬→아티스트 팔로우는 정의상 target(followingId)이 곧 그 커뮤니티의 주인이라 following_id == community_id다.
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
