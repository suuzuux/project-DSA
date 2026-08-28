package megane6.weplanet.repository.community;

import megane6.weplanet.domain.entity.community.CommunityProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CommunityProfileRepository extends JpaRepository<CommunityProfile, Long> {
	Optional<CommunityProfile> findByCommunityMember_Id(Long communityMemberId);
}