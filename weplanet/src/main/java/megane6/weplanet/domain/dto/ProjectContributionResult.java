package megane6.weplanet.domain.dto;

public record ProjectContributionResult(
        boolean success,
        Long contributionId,
        String orderNo,
        Long amount,
        Long fundedAmount,
        Long participantCount,
        int progressPercent,
        String message
) {
}
