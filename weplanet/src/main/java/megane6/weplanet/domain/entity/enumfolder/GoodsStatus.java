package megane6.weplanet.domain.entity.enumfolder;

public enum GoodsStatus {
	ON_SALE("판매중"),
	HIDDEN("비공개");

	private final String label;

	GoodsStatus(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}
}
