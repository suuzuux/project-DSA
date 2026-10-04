package megane6.weplanet.domain.entity.enumfolder;

/**
 * 굿즈샵 필터(전체 / MD · 굿즈 / 디지털 / 멤버십전용)에 대응하는 등록 카테고리.
 * 의류·신발 등 {@link GoodsCategoryType} 과는 별개다.
 */
public enum GoodsShopCategory {
	MD("md", "goods.shopCategory.md", "MD · 굿즈", "goods-cat--md"),
	DIGITAL("digital", "goods.shopCategory.digital", "디지털", "goods-cat--digital"),
	MEMBERSHIP("membership", "goods.shopCategory.membership", "멤버십전용", "goods-cat--membership");

	private final String filterKey;
	// 메시지 키 - 실제 화면에 보여줄 문구는 이 키로 MessageSource에서 로케일에 맞게 조회한다.
	private final String messageKey;
	// 아래 label은 messages*.properties 없이도 참고할 수 있는 한국어 기본값. 새 코드는
	// getLabel() 대신 messageKey + MessageSource를 쓸 것.
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
