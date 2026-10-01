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
	
	// SETTINGS-03: "{코드}" 형식으로 넘기면 Bean Validation이 messages.properties(MessageSource)에서
	// 현재 세션 로케일에 맞는 문구를 찾아 채워준다 (Spring Boot의 LocalValidatorFactoryBean 기본 동작).
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
	// AUTH-11: real_name 컬럼은 VARBINARY(255)(UTF-8 바이트)라 한글 약 86자를 넘으면 DB 오류(500)가 났다
	@Size(max = 50, message = "{signup.validation.realNameTooLong}")
	private String realName;
	
	@NotBlank(message = "{signup.validation.emailRequired}")
	@Email(message = "{signup.validation.emailFormat}")
	private String email;

	// (선택) 광고 및 마케팅 활용 동의 - User.marketingConsent와 같은 값. 체크 안 하면 false로 바인딩됨
	// (Thymeleaf th:field가 checkbox에 hidden fallback을 자동으로 넣어줌).
	private boolean marketingConsent;
	
	public boolean isPasswordConfirmed() {
		return password != null && password.equals(passwordConfirm);
	}
}