package megane6.weplanet.domain.entity.enumfolder;

public enum GoodsCategoryType {
	CLOTHING("goods.categoryType.clothing", "의류", "goods-cat--clothing", true),
	SHOES("goods.categoryType.shoes", "신발", "goods-cat--shoes", true),
	BAG("goods.categoryType.bag", "가방", "goods-cat--bag", false),
	ACCESSORY("goods.categoryType.accessory", "악세서리", "goods-cat--accessory", false),
	OTHER("goods.categoryType.other", "기타", "goods-cat--other", false);

	// 화면 표시용 메시지 키
	private final String messageKey;
	// 한국어 기본값 (화면은 messageKey 사용)
	private final String label;
	private final String chipClass;
	/** true 면 구매자가 고르는 사이즈 옵션이 있다 */
	private final boolean hasSelectableOptions;

	GoodsCategoryType(String messageKey, String label, String chipClass, boolean hasSelectableOptions) {
		this.messageKey = messageKey;
		this.label = label;
		this.chipClass = chipClass;
		this.hasSelectableOptions = hasSelectableOptions;
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

	public boolean isHasSelectableOptions() {
		return hasSelectableOptions;
	}
}
