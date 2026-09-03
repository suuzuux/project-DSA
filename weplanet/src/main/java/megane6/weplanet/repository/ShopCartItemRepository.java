package megane6.weplanet.repository;

import megane6.weplanet.domain.entity.ShopCartItem;
import megane6.weplanet.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShopCartItemRepository extends JpaRepository<ShopCartItem, Long> {

	List<ShopCartItem> findByUserOrderByCreatedAtAsc(User user);

	Optional<ShopCartItem> findByIdAndUser(Long id, User user);

	Optional<ShopCartItem> findByUserAndProductId(User user, String productId);

	long countByUser(User user);
}
