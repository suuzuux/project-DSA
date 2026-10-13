package megane6.weplanet.repository.main;

import megane6.weplanet.domain.entity.MainBanner;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MainBannerRepository extends JpaRepository<MainBanner, Long> {

	// 관리 목록 - 노출 순서 (같으면 최근 등록 우선)
	@EntityGraph(attributePaths = {"artist", "goods"})
	List<MainBanner> findAllByOrderBySortOrderAscIdDesc();

	// 메인 페이지 - 노출 중인 배너만
	@EntityGraph(attributePaths = {"artist", "goods"})
	List<MainBanner> findByActiveTrueOrderBySortOrderAscIdDesc();
}
