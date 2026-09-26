package megane6.weplanet.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "shop_order_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShopOrderItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id", nullable = false)
	private ShopOrder order;

	@Column(name = "goods_id", nullable = false)
	private Long goodsId;

	@Column(name = "variant_id", nullable = false)
	private Long variantId;

	@Column(name = "product_id", nullable = false, length = 100)
	private String productId;

	@Column(name = "product_name", nullable = false, length = 200)
	private String productName;

	@Column(nullable = false)
	private int quantity;

	@Column(name = "unit_price", nullable = false)
	private int unitPrice;

	private ShopOrderItem(ShopOrder order, Long goodsId, Long variantId, String productId,
						  String productName, int quantity, int unitPrice) {
		this.order = order;
		this.goodsId = goodsId;
		this.variantId = variantId;
		this.productId = productId;
		this.productName = productName;
		this.quantity = quantity;
		this.unitPrice = unitPrice;
	}

	public static ShopOrderItem of(ShopOrder order, Long goodsId, Long variantId, String productId,
								   String productName, int quantity, int unitPrice) {
		return new ShopOrderItem(order, goodsId, variantId, productId, productName, quantity, unitPrice);
	}
}
