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

	public void requireComplete() {
		if (isBlank(receiverName)) {
			throw new IllegalArgumentException("받는 사람을 입력해주세요.");
		}
		if (receiverName.length() > 50) {
			throw new IllegalArgumentException("받는 사람 이름이 너무 깁니다.");
		}
		if (isBlank(receiverPhone)) {
			throw new IllegalArgumentException("연락처를 입력해주세요.");
		}
		if (receiverPhone.length() > 30) {
			throw new IllegalArgumentException("연락처가 너무 깁니다.");
		}
		if (isBlank(zipcode) || isBlank(address1)) {
			throw new IllegalArgumentException("주소를 검색해주세요.");
		}
		if (zipcode.length() > 10) {
			throw new IllegalArgumentException("우편번호가 올바르지 않습니다.");
		}
		if (address1.length() > 255) {
			throw new IllegalArgumentException("주소가 너무 깁니다.");
		}
		if (address2 != null && address2.length() > 200) {
			throw new IllegalArgumentException("상세 주소가 너무 깁니다.");
		}
		if (deliveryMemo != null && deliveryMemo.length() > 200) {
			throw new IllegalArgumentException("배송 메모가 너무 깁니다.");
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
