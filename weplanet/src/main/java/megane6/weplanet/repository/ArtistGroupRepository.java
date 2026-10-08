package megane6.weplanet.repository;

import megane6.weplanet.domain.dto.community.ArtistSearchRow;
import megane6.weplanet.domain.entity.ArtistGroup;
import megane6.weplanet.domain.entity.enumfolder.GroupGender;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ArtistGroupRepository extends JpaRepository<ArtistGroup, Long> {

	// 그룹 이름 UNIQUE - 저장 전에 미리 확인한다.
	boolean existsByName(String name);

	// 커뮤니티 영문 주소 조회 (대소문자 무시, 중복 시 가장 먼저 만든 그룹).
	Optional<ArtistGroup> findFirstByNameEnOrderByIdAsc(String nameEn);

	boolean existsByNameEn(String nameEn);

	// 커뮤니티 탐색 - 선택 필터(null 이면 무시), 활동 중인 아티스트만.
	@Query("""
			SELECT new megane6.weplanet.domain.dto.community.ArtistSearchRow(
				u.id, u.nickname, g.gender, g.memberCount, g.nationality, g.category, g.debutDate)
			FROM ArtistGroup g
			JOIN User u ON u.id = g.id
			WHERE u.status = megane6.weplanet.domain.entity.enumfolder.UserStatus.ACTIVE
			  AND (:keyword IS NULL OR u.nickname LIKE CONCAT('%', :keyword, '%') OR g.nameEn LIKE CONCAT('%', :keyword, '%'))
			  AND (:gender IS NULL OR g.gender = :gender)
			  AND (:nationality IS NULL OR g.nationality = :nationality)
			  AND (:category IS NULL OR g.category = :category)
			  AND (:memberCount IS NULL OR g.memberCount = :memberCount)
			  AND (:isSolo IS NULL
			       OR (:isSolo = true AND g.memberCount = 1)
			       OR (:isSolo = false AND g.memberCount > 1))
			  AND (:debutFrom IS NULL OR g.debutDate >= :debutFrom)
			  AND (:debutTo IS NULL OR g.debutDate <= :debutTo)
			ORDER BY u.nickname ASC
			""")
	List<ArtistSearchRow> search(
			@Param("keyword") String keyword,
			@Param("gender") GroupGender gender,
			@Param("nationality") String nationality,
			@Param("category") String category,
			@Param("memberCount") Integer memberCount,
			@Param("isSolo") Boolean isSolo,
			@Param("debutFrom") LocalDate debutFrom,
			@Param("debutTo") LocalDate debutTo
	);
}
