package megane6.weplanet.domain.dto;

import megane6.weplanet.domain.entity.ProjectContribution;
import megane6.weplanet.domain.entity.enumfolder.FanProjectPaymentStatus;
import megane6.weplanet.domain.entity.enumfolder.SettlementBank;

import java.time.LocalDateTime;

/** 내 프로젝트 참여 기록 카드 (입금 대기일 때만 계좌 표시, secret 제외). */
public record ProjectParticipationView(
		Long contributionId,
		Long projectId,
		Long artistId,
		String projectTitle,
		Long amount,
		String statusLabel,
		// 화면 번역용 키 (statusLabel 은 폴백)
		String statusMessageKey,
		String statusCode,
		boolean waitingForDeposit,
		boolean anonymous,
		LocalDateTime orderedAt,
		LocalDateTime paidAt,
		String bankName,
		String bankMessageKey,
		String accountNumber,
		LocalDateTime dueDate
) {
	
	public static ProjectParticipationView from(ProjectContribution contribution) {
		FanProjectPaymentStatus status = contribution.getPaymentStatus();
		boolean waiting = status == FanProjectPaymentStatus.WAITING_FOR_DEPOSIT;
		
		return new ProjectParticipationView(
				contribution.getId(),
				contribution.getProject().getId(),
				contribution.getProject().getArtist().getId(),
				contribution.getProject().getTitle(),
				contribution.getAmount(),
				status.getDisplayName(),
				status.getMessageKey(),
				status.getBadgeCode(),
				waiting,
				contribution.isAnonymous(),
				contribution.getCreatedAt(),
				contribution.getPaidAt(),
				waiting ? SettlementBank.displayNameOfTossCode(contribution.getVirtualBankCode()) : null,
				waiting ? SettlementBank.messageKeyOfTossCode(contribution.getVirtualBankCode()) : null,
				waiting ? contribution.getVirtualAccountNumber() : null,
				waiting ? contribution.getDueDate() : null
		);
	}
}