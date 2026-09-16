package megane6.weplanet.domain.entity.enumfolder;

public enum GoodsCategoryType {
	CLOTHING("의류", "goods-cat--clothing"),
	ACCESSORY("악세서리", "goods-cat--accessory"),
	BAG("가방", "goods-cat--bag"),
	SHOES("신발", "goods-cat--shoes"),
	OTHER("기타", "goods-cat--other");

	private final String label;
	private final String chipClass;

	GoodsCategoryType(String label, String chipClass) {
		this.label = label;
		this.chipClass = chipClass;
	}

	public String getLabel() {
		return label;
	}

	public String getChipClass() {
		return chipClass;
	}
}
