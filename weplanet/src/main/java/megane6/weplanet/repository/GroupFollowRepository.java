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

    // 특정 아티스트(groupId)를 팔로우하는 팬 목록. GroupFollow에는 User 연관관계가 없어서, 여기서 얻은
    // fanId들을 UserRepository.findAllById(...)로 다시 조회해야 한다.
    // (참고: 이벤트·혜택 알림 이메일의 발송 대상은 "가입"(CommunityMember) 기준이라 이 메서드는 안 씀 -
    // About 위젯 팔로우 버튼 등 순수 팔로우 관련 기능에서 필요해지면 쓸 것)
    List<GroupFollow> findByGroupId(Long groupId);
}
