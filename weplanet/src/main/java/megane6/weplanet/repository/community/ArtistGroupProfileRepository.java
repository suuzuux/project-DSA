package megane6.weplanet.repository.community;

import megane6.weplanet.domain.dto.community.ArtistSearchRow;
import megane6.weplanet.domain.entity.community.ArtistGroupProfile;
import megane6.weplanet.domain.entity.enumfolder.GroupGender;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ArtistGroupProfileRepository extends JpaRepository<ArtistGroupProfile, Long> {
	
	// 필터는 전부 선택사항 - null로 넘기면 그 조건은 무시됨 (검색창 처음 열었을 때 = 전체 목록)
	@Query("""
			SELECT new megane6.weplanet.domain.dto.community.ArtistSearchRow(
				u.id, u.nickname, agp.gender, agp.memberCount, agp.nationality, agp.category, agp.debutDate)
			FROM ArtistGroupProfile agp
			JOIN User u ON u.id = agp.artistId
			WHERE (:keyword IS NULL OR u.nickname LIKE CONCAT('%', :keyword, '%'))
			  AND (:gender IS NULL OR agp.gender = :gender)
			  AND (:nationality IS NULL OR agp.nationality = :nationality)
			  AND (:category IS NULL OR agp.category = :category)
			  AND (:memberCount IS NULL OR agp.memberCount = :memberCount)
			  AND (:isSolo IS NULL
			       OR (:isSolo = true AND agp.memberCount = 1)
			       OR (:isSolo = false AND agp.memberCount > 1))
			  AND (:debutFrom IS NULL OR agp.debutDate >= :debutFrom)
			  AND (:debutTo IS NULL OR agp.debutDate <= :debutTo)
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