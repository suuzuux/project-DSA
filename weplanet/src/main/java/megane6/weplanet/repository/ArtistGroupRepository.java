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

	// artist_groups.name 은 UNIQUE(uk_group_name) - 저장 전에 미리 확인해서 친절한 메시지를 준다
	boolean existsByName(String name);

	// 커뮤니티 영문 주소(/{name_en}). 컬럼 콜레이션이 utf8mb4_unicode_ci라 대소문자 구분 없이 비교된다.
	// 예전 데이터에 같은 영문명이 겹쳐 있을 수 있어 가장 먼저 만든 그룹 하나만 쓴다.
	Optional<ArtistGroup> findFirstByNameEnOrderByIdAsc(String nameEn);

	boolean existsByNameEn(String nameEn);

	// 커뮤니티 탐색 (예전 ArtistGroupProfileRepository.search). artist_groups.id == 아티스트 users.id
	// 필터는 전부 선택사항 - null로 넘기면 그 조건은 무시됨 (검색창 처음 열었을 때 = 전체 목록)
	// AUTH-11: 활동 중(ACTIVE)인 아티스트만 검색된다 - 아직 활성화 전(PENDING_ACTIVATION)이거나 정지·탈퇴된 아티스트 제외
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
