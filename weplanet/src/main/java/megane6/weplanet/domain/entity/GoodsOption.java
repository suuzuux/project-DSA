package megane6.weplanet.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import megane6.weplanet.domain.entity.enumfolder.GoodsCategoryType;

/** 카테고리별 옵션 값 (key-value). */
@Entity
@Table(name = "shop_goods_option", indexes = @Index(columnList = "goods_id, category"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoodsOption {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "goods_id", nullable = false)
	private Goods goods;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private GoodsCategoryType category;

	@Column(name = "option_key", nullable = false, length = 30)
	private String optionKey;

	@Column(name = "option_value", nullable = false, length = 500)
	private String optionValue;
}
