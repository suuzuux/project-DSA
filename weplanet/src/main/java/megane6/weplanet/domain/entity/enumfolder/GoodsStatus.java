package megane6.weplanet.domain.entity.enumfolder;

public enum GoodsStatus {
	ON_SALE("goods.status.onSale", "판매중"),
	HIDDEN("goods.status.hidden", "비공개");

	// 화면 표시용 메시지 키
	private final String messageKey;
	// 한국어 기본값 (화면은 messageKey 사용)
	private final String label;

	GoodsStatus(String messageKey, String label) {
		this.messageKey = messageKey;
		this.label = label;
	}

	public String getMessageKey() {
		return messageKey;
	}

	public String getLabel() {
		return label;
	}
}
