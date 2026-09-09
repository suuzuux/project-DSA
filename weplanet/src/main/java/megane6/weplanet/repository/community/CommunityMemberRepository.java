package megane6.weplanet.repository.community;

import megane6.weplanet.domain.dto.ArtistCount;
import megane6.weplanet.domain.entity.community.CommunityMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CommunityMemberRepository extends JpaRepository<CommunityMember, Long> {
	
	boolean existsByFanIdAndArtistId(Long fanId, Long artistId);
	
	Optional<CommunityMember> findByFanIdAndArtistId(Long fanId, Long artistId);
	
	void deleteByFanIdAndArtistId(Long fanId, Long artistId);
	
	List<CommunityMember> findByFanId(Long fanId);

	long countByArtistId(Long artistId);
	
	@Query("""
        select new megane6.weplanet.domain.dto.ArtistCount(
            member.artistId,
            count(member)
        )
        from CommunityMember member
        where member.artistId in :artistIds
        group by member.artistId
        """)
	List<ArtistCount> countMembersByArtistIds(
			@Param("artistIds") Collection<Long> artistIds
	);
}