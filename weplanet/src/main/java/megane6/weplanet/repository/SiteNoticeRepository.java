package megane6.weplanet.repository;

import megane6.weplanet.domain.entity.SiteNotice;
import megane6.weplanet.domain.entity.enumfolder.NoticeCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SiteNoticeRepository extends JpaRepository<SiteNotice, Long> {
	List<SiteNotice> findAllByOrderByCreatedAtDesc();
	List<SiteNotice> findByPinnedTrueOrderByPinOrderAsc();
	
	long countByPinnedTrue();
	
	@Query("""
        SELECT n FROM SiteNotice n
        WHERE (:category IS NULL OR n.category = :category)
          AND (:keyword IS NULL OR n.title LIKE CONCAT('%', :keyword, '%'))
        ORDER BY n.createdAt DESC
        """)
	List<SiteNotice> search(@Param("category") NoticeCategory category,
							@Param("keyword") String keyword);
	
	@Query("""
        SELECT n FROM SiteNotice n
        WHERE n.published = true
          AND (n.publishAt IS NULL OR n.publishAt <= CURRENT_TIMESTAMP)
          AND (:category IS NULL OR n.category = :category)
        ORDER BY n.createdAt DESC
        """)
	List<SiteNotice> findVisible(@Param("category") NoticeCategory category);
}
