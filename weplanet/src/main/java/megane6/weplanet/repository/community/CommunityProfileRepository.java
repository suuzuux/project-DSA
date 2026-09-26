package megane6.weplanet.repository.community;

import megane6.weplanet.domain.entity.community.CommunityProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CommunityProfileRepository extends JpaRepository<CommunityProfile, Long> {
	Optional<CommunityProfile> findByCommunityMember_Id(Long communityMemberId);

	@Query("""
			SELECT profile
			FROM CommunityProfile profile
			JOIN FETCH profile.communityMember member
			WHERE member.artistId = :artistId
			  AND member.fanId IN :authorIds
			""")
	List<CommunityProfile> findForAuthorsInCommunity(
			@Param("artistId") Long artistId,
			@Param("authorIds") Collection<Long> authorIds);
}
