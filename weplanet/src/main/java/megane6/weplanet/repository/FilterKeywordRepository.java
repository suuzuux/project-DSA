package megane6.weplanet.repository;

import megane6.weplanet.domain.entity.FilterKeyword;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface FilterKeywordRepository extends JpaRepository<FilterKeyword, Long> {
    // 등록된 금칙어를 문자열 리스트로 바로 꺼내옴
    @Query("select f.keyword from FilterKeyword f")
    List<String> findAllKeywords();
}
