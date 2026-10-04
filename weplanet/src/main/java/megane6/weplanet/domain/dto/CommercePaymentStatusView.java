package megane6.weplanet.domain.dto;

import megane6.weplanet.domain.entity.enumfolder.FanProjectPaymentStatus;

public record CommercePaymentStatusView(
		String status,
		String displayName,
		boolean paid,
		boolean finished
) {
	// 상태 라벨은 호출부(ShopPaymentService·MembershipPaymentService)가 현재 로케일로 번역해서 넘긴다 (ProjectPaymentStatusView 와 같은 방식)
	public static CommercePaymentStatusView from(FanProjectPaymentStatus status, String statusLabel) {
		return new CommercePaymentStatusView(
				status.name(),
				statusLabel,
				status == FanProjectPaymentStatus.PAID,
				status != FanProjectPaymentStatus.WAITING_FOR_DEPOSIT
		);
	}
}
