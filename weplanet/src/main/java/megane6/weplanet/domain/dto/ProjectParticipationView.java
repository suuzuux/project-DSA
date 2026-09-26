package megane6.weplanet.domain.dto;

import megane6.weplanet.domain.entity.ProjectContribution;
import megane6.weplanet.domain.entity.enumfolder.FanProjectPaymentStatus;
import megane6.weplanet.domain.entity.enumfolder.SettlementBank;

import java.time.LocalDateTime;

/**
 * 나의 컬렉션 > 내 프로젝트 참여 기록 카드 한 장
 *
 * waitingForDeposit 이 true 일 때만 계좌 정보(bankName, accountNumber, dueDate)를 보여준다.
 * 웹훅 검증용 secret 같은 민감 정보는 넣지 않는다.
 */
public record ProjectParticipationView(
		Long contributionId,
		Long projectId,
		Long artistId,
		String projectTitle,
		Long amount,
		String statusLabel,
		String statusCode,
		boolean waitingForDeposit,
		boolean anonymous,
		LocalDateTime orderedAt,
		LocalDateTime paidAt,
		String bankName,
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
				status.getBadgeCode(),
				waiting,
				contribution.isAnonymous(),
				contribution.getCreatedAt(),
				contribution.getPaidAt(),
				waiting ? SettlementBank.displayNameOfTossCode(contribution.getVirtualBankCode()) : null,
				waiting ? contribution.getVirtualAccountNumber() : null,
				waiting ? contribution.getDueDate() : null
		);
	}
}