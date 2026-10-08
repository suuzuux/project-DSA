package megane6.weplanet.domain.entity.enumfolder;

/** 굿즈샵 필터 카테고리 (GoodsCategoryType 과 별개). */
public enum GoodsShopCategory {
	MD("md", "goods.shopCategory.md", "MD · 굿즈", "goods-cat--md"),
	DIGITAL("digital", "goods.shopCategory.digital", "디지털", "goods-cat--digital"),
	MEMBERSHIP("membership", "goods.shopCategory.membership", "멤버십전용", "goods-cat--membership");

	private final String filterKey;
	// 화면 표시용 메시지 키
	private final String messageKey;
	// 한국어 기본값 (화면은 messageKey 사용)
	private final String label;
	private final String chipClass;

	GoodsShopCategory(String filterKey, String messageKey, String label, String chipClass) {
		this.filterKey = filterKey;
		this.messageKey = messageKey;
		this.label = label;
		this.chipClass = chipClass;
	}

	public String getFilterKey() {
		return filterKey;
	}

	public String getMessageKey() {
		return messageKey;
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
