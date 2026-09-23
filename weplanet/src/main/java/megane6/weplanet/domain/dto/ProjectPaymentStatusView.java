package megane6.weplanet.domain.dto;

import megane6.weplanet.domain.entity.ProjectContribution;
import megane6.weplanet.domain.entity.enumfolder.FanProjectPaymentStatus;

/**
 * 가상계좌 안내 화면이 입금 여부를 물어볼 때 돌려주는 값.
 * 계좌번호나 웹훅 secret 같은 값은 담지 않는다. (이미 화면에 그려진 정보만 갱신하면 됨)
 *
 * finished : 더 기다려도 바뀌지 않는 상태 -> 화면에서 폴링을 멈추면 된다.
 */
public record ProjectPaymentStatusView(
		String status,
		String displayName,
		boolean paid,
		boolean finished
) {
	public static ProjectPaymentStatusView from(ProjectContribution contribution) {
		FanProjectPaymentStatus status = contribution.getPaymentStatus();
		return new ProjectPaymentStatusView(
				status.name(),
				status.getDisplayName(),
				status == FanProjectPaymentStatus.PAID,
				status != FanProjectPaymentStatus.WAITING_FOR_DEPOSIT
		);
	}
}
