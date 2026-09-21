package megane6.weplanet.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import megane6.weplanet.domain.entity.enumfolder.GoodsCategoryType;

@Entity
@Table(
		name = "shop_goods_category",
		uniqueConstraints = @UniqueConstraint(columnNames = {"goods_id", "category"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoodsCategoryLink {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "goods_id", nullable = false)
	private Goods goods;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private GoodsCategoryType category;
}
