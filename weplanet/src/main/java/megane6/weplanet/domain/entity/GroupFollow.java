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

// 와이어프레임 26번: 팬이 아티스트(그룹)를 팔로우한 기록 하나.
// 기존 group_follow 테이블(fan_id, group_id 복합 PK)을 그대로 매핑함.
// group_id는 MediaGroupDataInitializer가 artist_groups.id == 아티스트 User.id로 맞춰 시딩해두므로,
// 여기서는 group_id를 그냥 "그 아티스트의 User.id"로 취급해서 씀 (커뮤니티 artistId와 동일)
@Entity
@Table(name = "group_follow")
@IdClass(GroupFollow.Pk.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupFollow {

    @Id
    @Column(name = "fan_id")
    private Long fanId;

    @Id
    @Column(name = "group_id")
    private Long groupId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pk implements Serializable {
        private Long fanId;
        private Long groupId;
    }
}
