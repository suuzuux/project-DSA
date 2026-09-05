package megane6.weplanet.repository;

import megane6.weplanet.domain.entity.SiteNotice;
import megane6.weplanet.domain.entity.enumfolder.NoticeCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SiteNoticeRepository extends JpaRepository<SiteNotice, Long> {
	List<SiteNotice> findAllByOrderByCreatedAtDesc();
	List<SiteNotice> findByPublishedTrueOrderByCreatedAtDesc();
	List<SiteNotice> findByPinnedTrueOrderByPinOrderAsc();
	List<SiteNotice> findByPublishedTrueAndCategoryOrderByCreatedAtDesc(
			NoticeCategory category);
	
	long countByPinnedTrue();
	
	@Query("""
        SELECT n FROM SiteNotice n
        WHERE (:category IS NULL OR n.category = :category)
          AND (:keyword IS NULL OR n.title LIKE CONCAT('%', :keyword, '%'))
        ORDER BY n.createdAt DESC
        """)
	List<SiteNotice> search(@Param("category") NoticeCategory category,
							@Param("keyword") String keyword);
}
