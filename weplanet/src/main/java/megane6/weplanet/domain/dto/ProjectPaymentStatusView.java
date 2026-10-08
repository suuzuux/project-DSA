package megane6.weplanet.domain.dto;

import megane6.weplanet.domain.entity.ProjectContribution;
import megane6.weplanet.domain.entity.enumfolder.FanProjectPaymentStatus;

/** 입금 여부 확인 응답 (finished 면 화면이 폴링을 멈춤). */
public record ProjectPaymentStatusView(
		String status,
		String displayName,
		boolean paid,
		boolean finished
) {
	public static ProjectPaymentStatusView from(ProjectContribution contribution) {
		return from(contribution, contribution.getPaymentStatus().getDisplayName());
	}

	// 상태 라벨은 호출부가 번역해서 넘긴다.
	public static ProjectPaymentStatusView from(ProjectContribution contribution, String statusLabel) {
		FanProjectPaymentStatus status = contribution.getPaymentStatus();
		return new ProjectPaymentStatusView(
				status.name(),
				statusLabel,
				status == FanProjectPaymentStatus.PAID,
				status != FanProjectPaymentStatus.WAITING_FOR_DEPOSIT
		);
	}
}
