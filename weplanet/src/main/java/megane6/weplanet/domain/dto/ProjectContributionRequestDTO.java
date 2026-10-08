package megane6.weplanet.domain.dto;

import jakarta.validation.constraints.*;

// 검증 메시지는 {메시지 키} 참조 방식

public record ProjectContributionRequestDTO(
        @NotNull(message = "{project.contribution.validation.amountRequired}")
        @Min(value = 1_000, message = "{project.contribution.validation.amountMin}")
        @Max(value = 3_000_000, message = "{project.contribution.validation.amountMax}")
        Long amount,
        boolean anonymous,
        @AssertTrue(message = "{community.project.js.refundRequired}")
        boolean refundPolicyAgreed,
        
        @NotBlank(message = "{project.contribution.validation.idempotencyKeyRequired}")
        @Size(max = 64, message = "{project.contribution.validation.idempotencyKeyInvalid}")
        String idempotencyKey
) {
}
