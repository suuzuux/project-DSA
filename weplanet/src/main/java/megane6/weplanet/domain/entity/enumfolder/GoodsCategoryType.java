package megane6.weplanet.domain.entity.enumfolder;

public enum GoodsCategoryType {
	CLOTHING("goods.categoryType.clothing", "의류", "goods-cat--clothing", true),
	SHOES("goods.categoryType.shoes", "신발", "goods-cat--shoes", true),
	BAG("goods.categoryType.bag", "가방", "goods-cat--bag", false),
	ACCESSORY("goods.categoryType.accessory", "악세서리", "goods-cat--accessory", false),
	OTHER("goods.categoryType.other", "기타", "goods-cat--other", false);

	// SETTINGS-03: 메시지 키 - 실제 화면에 보여줄 문구는 이 키로 MessageSource에서 로케일에 맞게 조회한다.
	private final String messageKey;
	// 한국어 기본값(과거 하드코딩 값). 새 코드는 getLabel() 대신 messageKey + MessageSource를 쓸 것.
	private final String label;
	private final String chipClass;
	/** true면 구매자가 고르는 사이즈/치수 Variant 가 생긴다 */
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
