package megane6.weplanet.domain.dto;

import megane6.weplanet.domain.entity.ProjectContribution;
import megane6.weplanet.domain.entity.enumfolder.FanProjectPaymentStatus;
import megane6.weplanet.domain.entity.enumfolder.SettlementBank;

import java.time.LocalDateTime;

// 가상계좌 발급 안내 화면에 보여줄 값 (웹훅 secret 같은 민감 정보는 넣지 않는다)
public record ProjectPaymentResultView(
		Long artistId,
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
				contribution.getProject().getTitle(),
				contribution.getAmount(),
				SettlementBank.displayNameOfTossCode(contribution.getVirtualBankCode()),
				contribution.getVirtualAccountNumber(),
				contribution.getDueDate(),
				contribution.getPaymentStatus() == FanProjectPaymentStatus.PAID
		);
	}
}
