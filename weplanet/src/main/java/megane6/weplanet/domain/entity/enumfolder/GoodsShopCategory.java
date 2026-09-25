package megane6.weplanet.domain.entity.enumfolder;

/**
 * 굿즈샵 필터(전체 / MD · 굿즈 / 디지털 / 멤버십전용)에 대응하는 등록 카테고리.
 * 의류·신발 등 {@link GoodsCategoryType} 과는 별개다.
 */
public enum GoodsShopCategory {
	MD("md", "MD · 굿즈", "goods-cat--md"),
	DIGITAL("digital", "디지털", "goods-cat--digital"),
	MEMBERSHIP("membership", "멤버십전용", "goods-cat--membership");

	private final String filterKey;
	private final String label;
	private final String chipClass;

	GoodsShopCategory(String filterKey, String label, String chipClass) {
		this.filterKey = filterKey;
		this.label = label;
		this.chipClass = chipClass;
	}

	public String getFilterKey() {
		return filterKey;
	}

	public String getLabel() {
		return label;
	}

	public String getChipClass() {
		return chipClass;
	}

	public boolean isMembershipOnly() {
		return this == MEMBERSHIP;
	}

	public static GoodsShopCategory fromOrDefault(GoodsShopCategory value) {
		return value == null ? MD : value;
	}
}
