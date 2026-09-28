package megane6.weplanet.repository;

import megane6.weplanet.domain.entity.ArtistGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ArtistGroupRepository extends JpaRepository<ArtistGroup, Long> {

	// artist_groups.name 은 UNIQUE(uk_group_name) - 저장 전에 미리 확인해서 친절한 메시지를 준다
	boolean existsByName(String name);

	// 커뮤니티 영문 주소(/{name_en}). 컬럼 콜레이션이 utf8mb4_unicode_ci라 대소문자 구분 없이 비교된다.
	// 예전 데이터에 같은 영문명이 겹쳐 있을 수 있어 가장 먼저 만든 그룹 하나만 쓴다.
	Optional<ArtistGroup> findFirstByNameEnOrderByIdAsc(String nameEn);

	boolean existsByNameEn(String nameEn);
}
