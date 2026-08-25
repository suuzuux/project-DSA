package megane6.weplanet.repository.community;

import megane6.weplanet.domain.entity.community.CommunityMember;
import megane6.weplanet.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CommunityMemberRepository extends JpaRepository<CommunityMember, Long> {
	
	Optional<CommunityMember> findByFanAndArtist(User fan, User artist);
	
	boolean existsByFanAndArtist(User fan, User artist);
	
	// EXPLORE-05: 이 팬이 가입한 아티스트만 최신 가입순으로
	@Query("select cm.artist from CommunityMember cm where cm.fan = :fan order by cm.joinedAt desc")
	List<User> findJoinedArtists(@Param("fan") User fan);
	
	// 검색 결과에 "가입중" 표시하려고 한 번에 조회 (N+1 방지)
	@Query("select cm.artist.id from CommunityMember cm where cm.fan = :fan")
	List<Long> findJoinedArtistIds(@Param("fan") User fan);
}