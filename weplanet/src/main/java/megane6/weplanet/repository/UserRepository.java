package megane6.weplanet.repository;

import megane6.weplanet.domain.dto.community.ArtistSearchRow;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
	
	// CHAT-06 AI팬 계정처럼, DB가 초기화돼도 항상 같은 이름으로 찾을 수 있어야 하는 경우 사용
	Optional<User> findByUsername(String username);
	
	boolean existsByUsername(String username);
	boolean existsByNickname(String nickname);
	boolean existsByEmail(String email);
	
	// DM 인박스(CHAT: 여러 아티스트 목록) 에서, 아직 대화 안 나눈 아티스트도 "추천" 칸에 보여주기 위해
	// 시스템에 있는 아티스트 전체 목록이 필요함
	List<User> findByRole(Role role);
	
	List<User> findByRoleAndNicknameContaining(Role role, String nickname);
	
	// EXPLORE-02 검색 - User -> ArtistGroupProfile이 단방향 관계라 JOIN FETCH 대신
	// constructor expression으로 한 번에 뽑아서 N+1을 원천 차단함.
	@org.springframework.data.jpa.repository.Query("""
          select new megane6.weplanet.domain.dto.community.ArtistSearchRow(
             u.id, u.nickname, agp.gender, agp.nationality, agp.category, agp.memberCount, agp.debutDate)
          from User u left join megane6.weplanet.domain.entity.community.ArtistGroupProfile agp on agp.artist = u
          where u.role = megane6.weplanet.domain.entity.enumfolder.Role.ARTIST
            and (:nickname is null or u.nickname like concat('%', :nickname, '%'))
            and (:gender is null or agp.gender = :gender)
            and (:nationality is null or agp.nationality like concat('%', :nationality, '%'))
            and (:category is null or agp.category like concat('%', :category, '%'))
            and (:minMembers is null or agp.memberCount >= :minMembers)
            and (:maxMembers is null or agp.memberCount <= :maxMembers)
            and (:debutFrom is null or agp.debutDate >= :debutFrom)
            and (:debutTo is null or agp.debutDate <= :debutTo)
          order by u.nickname
          """)
	List<ArtistSearchRow> searchArtists(
			@org.springframework.data.repository.query.Param("nickname") String nickname,
			@org.springframework.data.repository.query.Param("gender") megane6.weplanet.domain.entity.enumfolder.GroupGender gender,
			@org.springframework.data.repository.query.Param("nationality") String nationality,
			@org.springframework.data.repository.query.Param("category") String category,
			@org.springframework.data.repository.query.Param("minMembers") Integer minMembers,
			@org.springframework.data.repository.query.Param("maxMembers") Integer maxMembers,
			@org.springframework.data.repository.query.Param("debutFrom") java.time.LocalDate debutFrom,
			@org.springframework.data.repository.query.Param("debutTo") java.time.LocalDate debutTo
	);
}