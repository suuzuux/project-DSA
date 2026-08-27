package megane6.weplanet.repository.community;

import megane6.weplanet.domain.entity.community.CommunityMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommunityMemberRepository extends JpaRepository<CommunityMember, Long> {
	
	boolean existsByFanIdAndArtistId(Long fanId, Long artistId);
	
	Optional<CommunityMember> findByFanIdAndArtistId(Long fanId, Long artistId);
	
	void deleteByFanIdAndArtistId(Long fanId, Long artistId);
	
	List<CommunityMember> findByFanId(Long fanId);
}