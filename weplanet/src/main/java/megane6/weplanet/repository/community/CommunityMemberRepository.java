package megane6.weplanet.repository.community;

import megane6.weplanet.domain.dto.ArtistCount;
import megane6.weplanet.domain.entity.community.CommunityMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CommunityMemberRepository extends JpaRepository<CommunityMember, Long> {
	
	boolean existsByFanIdAndArtistId(Long fanId, Long artistId);
	
	Optional<CommunityMember> findByFanIdAndArtistId(Long fanId, Long artistId);
	
	void deleteByFanIdAndArtistId(Long fanId, Long artistId);
	
	// 한 사람이 가입한 모든 커뮤니티 (가입 정보 + 커뮤니티별 프로필)
	List<CommunityMember> findByFanId(Long fanId);

	List<CommunityMember> findByArtistId(Long artistId);

	// 게시글 작성자들의 이 커뮤니티 프로필을 한 번에 조회
	List<CommunityMember> findByArtistIdAndFanIdIn(Long artistId, Collection<Long> fanIds);

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

	// 급상승 커뮤니티: since 이후 커뮤니티별 신규 가입자 수
	@Query("""
        select new megane6.weplanet.domain.dto.ArtistCount(
            member.artistId,
            count(member)
        )
        from CommunityMember member
        where member.artistId in :artistIds
          and member.joinedAt >= :since
        group by member.artistId
        """)
	List<ArtistCount> countNewMembersByArtistIds(
			@Param("artistIds") Collection<Long> artistIds,
			@Param("since") LocalDateTime since
	);
}