package megane6.weplanet.repository;

import megane6.weplanet.domain.entity.SiteNotice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SiteNoticeRepository extends JpaRepository<SiteNotice, Long> {
	List<SiteNotice> findAllByOrderByCreatedAtDesc();
	List<SiteNotice> findByPublishedTrueOrderByCreatedAtDesc();
}
