package megane6.weplanet.repository;

import megane6.weplanet.domain.entity.Goods;
import megane6.weplanet.domain.entity.GoodsOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GoodsOptionRepository extends JpaRepository<GoodsOption, Long> {

	List<GoodsOption> findByGoodsOrderByCategoryAscOptionKeyAsc(Goods goods);

	void deleteByGoods(Goods goods);
}
