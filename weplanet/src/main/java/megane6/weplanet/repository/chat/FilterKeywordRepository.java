package megane6.weplanet.repository.chat;

import megane6.weplanet.domain.entity.FilterKeyword;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface FilterKeywordRepository extends JpaRepository<FilterKeyword, Long> {

    // keyword 값만 문자열 목록으로 조회한다.
    @Query("select f.keyword from FilterKeyword f")
    List<String> findAllKeywords();
    
    List<FilterKeyword> findAllByOrderByKeywordAsc();
    boolean existsByKeywordIgnoreCase(String keyword);
    boolean existsByKeywordIgnoreCaseAndIdNot(
            String keyword,
            Long id
    );
}
