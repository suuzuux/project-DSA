package megane6.weplanet.domain.dto;

import megane6.weplanet.domain.entity.enumfolder.FanProjectPaymentStatus;

public record CommercePaymentStatusView(
		String status,
		String displayName,
		boolean paid,
		boolean finished
) {
	public static CommercePaymentStatusView from(FanProjectPaymentStatus status) {
		return new CommercePaymentStatusView(
				status.name(),
				status.getDisplayName(),
				status == FanProjectPaymentStatus.PAID,
				status != FanProjectPaymentStatus.WAITING_FOR_DEPOSIT
		);
	}
}
