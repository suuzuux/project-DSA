package megane6.weplanet.domain.dto;

import jakarta.validation.constraints.*;

public record ProjectContributionRequestDTO(
        @NotNull(message = "참여 금액을 입력해주세요.")
        @Min(value = 1_000, message = "참여 금액은 최소 1,000원 입니다.")
        @Max(value = 3_000_000, message = "한 번에 참여할 수 있는 금액은 최대 3,000,000원입니다.")
        Long amount,
        boolean anonymous,
        @AssertTrue(message = "환불 규정에 동의해주세요.")
        boolean refundPolicyAgreed,
        
        @NotBlank(message = "결제 요청 식별값이 없습니다.")
        @Size(max = 64, message = "결제 요청 식별값이 올바르지 않습니다.")
        String idempotencyKey
) {
}
