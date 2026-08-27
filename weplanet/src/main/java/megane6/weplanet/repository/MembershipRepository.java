package megane6.weplanet.repository;

import megane6.weplanet.domain.entity.Membership;
import megane6.weplanet.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MembershipRepository extends JpaRepository<Membership, Long> {

    // 특정 팬-아티스트 조합의 멤버십 조회 (DM 방 열 때 만료 여부 확인용)
    Optional<Membership> findByFanAndArtist(User fan, User artist);
}
