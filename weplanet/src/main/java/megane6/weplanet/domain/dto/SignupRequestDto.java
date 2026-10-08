package megane6.weplanet.domain.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SignupRequestDto {
	
	// {코드} 형식이면 현재 화면 언어 문구로 채워진다.
	@NotBlank(message = "{signup.validation.usernameRequired}")
	@Pattern(regexp = "^[a-zA-Z0-9]{4,20}$", message = "{signup.validation.usernamePattern}")
	private String username;
	
	@NotBlank(message = "{signup.validation.passwordRequired}")
	@Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*[0-9]).{8,20}$", message = "{signup.validation.passwordPattern}")
	private String password;
	
	@NotBlank(message = "{signup.validation.passwordConfirmRequired}")
	private String passwordConfirm;
	
	private String nickname; // 선택 입력 - 비어있으면 자동 생성
	
	@NotBlank(message = "{signup.validation.realNameRequired}")
	// real_name 은 VARBINARY(255) 라 50자로 제한한다.
	@Size(max = 50, message = "{signup.validation.realNameTooLong}")
	private String realName;
	
	@NotBlank(message = "{signup.validation.emailRequired}")
	@Email(message = "{signup.validation.emailFormat}")
	private String email;

	// 광고·마케팅 동의 (체크 안 하면 false).
	private boolean marketingConsent;
	
	public boolean isPasswordConfirmed() {
		return password != null && password.equals(passwordConfirm);
	}
}