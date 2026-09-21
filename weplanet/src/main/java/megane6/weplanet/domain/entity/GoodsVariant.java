package megane6.weplanet.domain.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * 판매 단위(SKU). 재고는 여기에만 둔다.
 * <ul>
 *   <li>의류: optionKey=SIZE, optionValue=S/M/L…</li>
 *   <li>신발: optionKey=SHOE_MM, optionValue=250…</li>
 *   <li>가방/악세서리/기타(또는 무카테고리): optionKey=DEFAULT, optionValue=""</li>
 * </ul>
 */
@Entity
@Table(
		name = "shop_goods_variant",
		uniqueConstraints = @UniqueConstraint(columnNames = {"goods_id", "option_key", "option_value"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoodsVariant {

	public static final String KEY_SIZE = "SIZE";
	public static final String KEY_SHOE_MM = "SHOE_MM";
	public static final String KEY_DEFAULT = "DEFAULT";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "goods_id", nullable = false)
	private Goods goods;

	@Column(name = "option_key", nullable = false, length = 30)
	private String optionKey;

	@Column(name = "option_value", nullable = false, length = 50)
	@Builder.Default
	private String optionValue = "";

	@Column(name = "stock_quantity", nullable = false)
	private int stockQuantity;

	public boolean isSoldOut() {
		return stockQuantity <= 0;
	}

	public boolean isLowStock() {
		return stockQuantity > 0 && stockQuantity < Goods.LOW_STOCK_THRESHOLD;
	}

	public boolean isSelectable() {
		return !KEY_DEFAULT.equals(optionKey);
	}

	public String displayLabel() {
		if (KEY_DEFAULT.equals(optionKey)) {
			return "기본";
		}
		if (KEY_SHOE_MM.equals(optionKey)) {
			return optionValue + "mm";
		}
		return optionValue;
	}
}
