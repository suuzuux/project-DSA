package megane6.weplanet.domain.dto;

/** 주문서 배송지 (결제 준비 시 주문에 스냅샷 저장). */
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

	/** 예외는 메시지 키로 던지고 GlobalExceptionHandler 가 번역한다. */
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
		// 상세 주소는 VARBINARY(512) 라 글자 수와 바이트 수를 모두 확인한다.
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
