package megane6.weplanet.domain.entity.enumfolder;

public enum GoodsCategoryType {
	CLOTHING("의류", "goods-cat--clothing", true),
	SHOES("신발", "goods-cat--shoes", true),
	BAG("가방", "goods-cat--bag", false),
	ACCESSORY("악세서리", "goods-cat--accessory", false),
	OTHER("기타", "goods-cat--other", false);

	private final String label;
	private final String chipClass;
	/** true면 구매자가 고르는 사이즈/치수 Variant 가 생긴다 */
	private final boolean hasSelectableOptions;

	GoodsCategoryType(String label, String chipClass, boolean hasSelectableOptions) {
		this.label = label;
		this.chipClass = chipClass;
		this.hasSelectableOptions = hasSelectableOptions;
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
