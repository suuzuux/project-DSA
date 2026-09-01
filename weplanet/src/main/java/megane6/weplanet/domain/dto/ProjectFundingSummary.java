package megane6.weplanet.domain.dto;

public record ProjectFundingSummary(
        Long projectId,
        Long fundedAmount,
        Long participantCount
) {
}
