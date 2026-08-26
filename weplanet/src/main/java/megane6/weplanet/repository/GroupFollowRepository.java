package megane6.weplanet.repository;

import megane6.weplanet.domain.entity.GroupFollow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GroupFollowRepository extends JpaRepository<GroupFollow, GroupFollow.Pk> {

    boolean existsByFanIdAndGroupId(Long fanId, Long groupId);

    void deleteByFanIdAndGroupId(Long fanId, Long groupId);

    // 와이어프레임 10번(급상승 커뮤니티 가입자수)에서도 재사용 예정
    long countByGroupId(Long groupId);

    List<GroupFollow> findByFanId(Long fanId);
}
