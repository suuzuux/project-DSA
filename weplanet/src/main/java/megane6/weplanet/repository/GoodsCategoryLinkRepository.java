package megane6.weplanet.repository;

import megane6.weplanet.domain.entity.Goods;
import megane6.weplanet.domain.entity.GoodsCategoryLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GoodsCategoryLinkRepository extends JpaRepository<GoodsCategoryLink, Long> {

	List<GoodsCategoryLink> findByGoods(Goods goods);

	void deleteByGoods(Goods goods);
}
