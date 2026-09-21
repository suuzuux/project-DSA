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

	public static final int LOW_STOCK_THRESHOLD = 5;

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

	@Column(name = "membership_only", nullable = false)
	@Builder.Default
	private boolean membershipOnly = false;

	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	@OneToMany(mappedBy = "goods", cascade = CascadeType.ALL, orphanRemoval = true)
	@Builder.Default
	private List<GoodsCategoryLink> categoryLinks = new ArrayList<>();

	/** 가방 치수·메모 등 (재고 없음) */
	@OneToMany(mappedBy = "goods", cascade = CascadeType.ALL, orphanRemoval = true)
	@Builder.Default
	private List<GoodsOption> options = new ArrayList<>();

	/** 판매 SKU + 옵션별 재고 */
	@OneToMany(mappedBy = "goods", cascade = CascadeType.ALL, orphanRemoval = true)
	@Builder.Default
	private List<GoodsVariant> variants = new ArrayList<>();

	@PrePersist
	public void prePersist() {
		LocalDateTime now = LocalDateTime.now();
		this.createdAt = now;
		this.updatedAt = now;
		if (this.status == null) {
			this.status = GoodsStatus.ON_SALE;
		}
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

	public int getTotalStock() {
		if (variants == null || variants.isEmpty()) {
			return 0;
		}
		return variants.stream().mapToInt(GoodsVariant::getStockQuantity).sum();
	}

	/** 목록 표시용 별칭 — 총 재고 */
	public int getStockQuantity() {
		return getTotalStock();
	}

	public int getMinPositiveStock() {
		if (variants == null || variants.isEmpty()) {
			return 0;
		}
		return variants.stream()
				.mapToInt(GoodsVariant::getStockQuantity)
				.filter(s -> s > 0)
				.min()
				.orElse(0);
	}

	public boolean isSoldOut() {
		if (variants == null || variants.isEmpty()) {
			return true;
		}
		return variants.stream().allMatch(v -> v.getStockQuantity() <= 0);
	}

	public boolean isLowStock() {
		int min = getMinPositiveStock();
		return min > 0 && min < LOW_STOCK_THRESHOLD;
	}

	public List<GoodsCategoryType> getCategories() {
		return categoryLinks.stream()
				.map(GoodsCategoryLink::getCategory)
				.sorted(Comparator.comparing(Enum::name))
				.toList();
	}
}
