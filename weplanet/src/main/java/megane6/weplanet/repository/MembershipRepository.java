package megane6.weplanet.repository;

import megane6.weplanet.domain.entity.Membership;
import megane6.weplanet.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MembershipRepository extends JpaRepository<Membership, Long> {

    // 팬-아티스트 멤버십 조회 (DM 만료 확인용)
    Optional<Membership> findByFanAndArtist(User fan, User artist);

    long countByArtist(User artist);
}
