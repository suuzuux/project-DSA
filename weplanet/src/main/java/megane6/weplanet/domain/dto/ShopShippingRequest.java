package megane6.weplanet.domain.dto;

/**
 * 굿즈 주문서 배송지. 결제 준비 시 shop_order 에 스냅샷으로 저장한다.
 */
public record ShopShippingRequest(
		String receiverName,
		String receiverPhone,
		String zipcode,
		String address1,
		String address2,
		String deliveryMemo
) {
	public static ShopShippingRequest of(String receiverName, String receiverPhone, String zipcode,
										 String address1, String address2, String deliveryMemo) {
		return new ShopShippingRequest(
				trimToNull(receiverName),
				trimToNull(receiverPhone),
				trimToNull(zipcode),
				trimToNull(address1),
				trimToNull(address2),
				trimToNull(deliveryMemo)
		);
	}

	/**
	 * SETTINGS-03: 예외 메시지는 메시지 키로 던지고, 화면으로 내보내는 쪽(GlobalExceptionHandler)에서
	 * Messages.resolve()로 현재 로케일 문구로 바꾼다.
	 */
	public void requireComplete() {
		if (isBlank(receiverName)) {
			throw new IllegalArgumentException("shop.error.receiverNameRequired");
		}
		if (receiverName.length() > 50) {
			throw new IllegalArgumentException("shop.error.receiverNameTooLong");
		}
		if (isBlank(receiverPhone)) {
			throw new IllegalArgumentException("shop.error.receiverPhoneRequired");
		}
		if (receiverPhone.length() > 30) {
			throw new IllegalArgumentException("shop.error.receiverPhoneTooLong");
		}
		if (isBlank(zipcode) || isBlank(address1)) {
			throw new IllegalArgumentException("shop.error.addressRequired");
		}
		if (zipcode.length() > 10) {
			throw new IllegalArgumentException("shop.error.zipcodeInvalid");
		}
		if (address1.length() > 255) {
			throw new IllegalArgumentException("shop.error.addressTooLong");
		}
		// AUTH-11: 상세 주소 컬럼은 VARBINARY(512)(UTF-8 바이트)라 "200자" 기준으로는 한글 171자부터 DB 오류(500)가 났다.
		// 글자 수와 실제 저장 크기(바이트)를 둘 다 확인한다.
		if (address2 != null && (address2.length() > 200
				|| address2.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 512)) {
			throw new IllegalArgumentException("shop.error.address2TooLong");
		}
		if (deliveryMemo != null && deliveryMemo.length() > 200) {
			throw new IllegalArgumentException("shop.error.deliveryMemoTooLong");
		}
	}

	private static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
