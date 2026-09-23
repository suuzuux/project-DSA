package megane6.weplanet.domain.dto;

import megane6.weplanet.domain.entity.ProjectContribution;
import megane6.weplanet.domain.entity.enumfolder.FanProjectPaymentStatus;
import megane6.weplanet.domain.entity.enumfolder.SettlementBank;

import java.time.LocalDateTime;

// 가상계좌 발급 안내 화면에 보여줄 값 (웹훅 secret 같은 민감 정보는 넣지 않는다)
public record ProjectPaymentResultView(
		Long artistId,
		// 안내 화면이 입금 여부를 물어볼 때 쓰는 주문번호 (민감 정보 아님)
		String orderNo,
		String projectTitle,
		Long amount,
		String bankName,
		String accountNumber,
		LocalDateTime dueDate,
		boolean paid
) {
	public static ProjectPaymentResultView from(ProjectContribution contribution) {
		return new ProjectPaymentResultView(
				contribution.getProject().getArtist().getId(),
				contribution.getOrderNo(),
				contribution.getProject().getTitle(),
				contribution.getAmount(),
				SettlementBank.displayNameOfTossCode(contribution.getVirtualBankCode()),
				contribution.getVirtualAccountNumber(),
				contribution.getDueDate(),
				contribution.getPaymentStatus() == FanProjectPaymentStatus.PAID
		);
	}
}
