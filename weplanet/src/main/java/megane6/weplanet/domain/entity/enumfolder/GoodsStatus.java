package megane6.weplanet.domain.entity.enumfolder;

public enum GoodsStatus {
	ON_SALE("goods.status.onSale", "판매중"),
	HIDDEN("goods.status.hidden", "비공개");

	// 메시지 키 - 실제 화면에 보여줄 문구는 이 키로 MessageSource에서 로케일에 맞게 조회한다.
	private final String messageKey;
	// 한국어 기본값. 새 코드는 getLabel() 대신 messageKey + MessageSource를 쓸 것.
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
