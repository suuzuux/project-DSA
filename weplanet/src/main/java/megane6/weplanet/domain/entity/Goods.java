package megane6.weplanet.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import megane6.weplanet.domain.entity.enumfolder.GoodsCategoryType;
import megane6.weplanet.domain.entity.enumfolder.GoodsStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Entity
@Table(name = "shop_goods")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Goods {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "artist_id", nullable = false)
	private User artist;

	@Column(nullable = false, length = 200)
	private String name;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Column(nullable = false)
	private int price;

	@Column(name = "thumbnail_url", length = 500)
	private String thumbnailUrl;

	@Column(name = "official_url", length = 500)
	private String officialUrl;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private GoodsStatus status;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	/** 판매 가능 재고. 품절 여부는 status가 아니라 이 값(0)으로 판정한다. */
	@Column(name = "stock_quantity", nullable = false)
	private int stockQuantity;

	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	@OneToMany(mappedBy = "goods", cascade = CascadeType.ALL, orphanRemoval = true)
	@Builder.Default
	private List<GoodsCategoryLink> categoryLinks = new ArrayList<>();

	@OneToMany(mappedBy = "goods", cascade = CascadeType.ALL, orphanRemoval = true)
	@Builder.Default
	private List<GoodsOption> options = new ArrayList<>();

	@PrePersist
	public void prePersist() {
		LocalDateTime now = LocalDateTime.now();
		this.createdAt = now;
		this.updatedAt = now;
		if (this.status == null) {
			this.status = GoodsStatus.ON_SALE;
		}
	}

	public boolean isSoldOut() {
		return this.stockQuantity <= 0;
	}

	public boolean isLowStock() {
		return this.stockQuantity > 0 && this.stockQuantity < 5;
	}

	@PreUpdate
	public void preUpdate() {
		this.updatedAt = LocalDateTime.now();
	}

	public void softDelete() {
		this.deletedAt = LocalDateTime.now();
	}

	public boolean isDeleted() {
		return this.deletedAt != null;
	}

	public List<GoodsCategoryType> getCategories() {
		return categoryLinks.stream()
				.map(GoodsCategoryLink::getCategory)
				.sorted(Comparator.comparing(Enum::name))
				.toList();
	}
}
