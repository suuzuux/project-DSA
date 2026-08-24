package megane6.weplanet.repository;

import megane6.weplanet.domain.entity.FilterKeyword;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface FilterKeywordRepository extends JpaRepository<FilterKeyword, Long> {

    // @Query : 메서드 이름 규칙(findBy...)만으로는 표현하기 어려운 조회를 할 때,
    // 직접 JPQL(자바 객체 기준으로 쓰는 SQL과 비슷한 문법)을 적어주는 방법.
    // 여기서는 FilterKeyword 엔티티 전체가 아니라, keyword 필드 값(문자열)만 콕 집어서
    // List<String>으로 바로 꺼내오기 위해 사용함
    @Query("select f.keyword from FilterKeyword f")
    List<String> findAllKeywords();
}
