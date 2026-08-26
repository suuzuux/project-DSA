package megane6.weplanet.domain.dto.community;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommunityProfileRequestDto(
		@NotBlank(message = "닉네임을 입력해주세요.")
		@Size(max = 10, message = "닉네임은 10자 이내로 입력해주세요.")
		String nickname,
		
		@Size(max = 30, message = "소개글은 30자 이내로 입력해주세요.")
		String bio
) {
}