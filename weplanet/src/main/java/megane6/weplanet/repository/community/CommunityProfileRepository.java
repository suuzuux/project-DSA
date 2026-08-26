package megane6.weplanet.repository.community;

import megane6.weplanet.domain.entity.community.CommunityMember;
import megane6.weplanet.domain.entity.community.CommunityProfile;
import megane6.weplanet.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CommunityProfileRepository extends JpaRepository<CommunityProfile, Long> {
	Optional<CommunityProfile> findByCommunityMember(CommunityMember communityMember);
	
	Optional<CommunityProfile> findTopByCommunityMember_FanOrderByUpdatedAtDesc(User fan);
}