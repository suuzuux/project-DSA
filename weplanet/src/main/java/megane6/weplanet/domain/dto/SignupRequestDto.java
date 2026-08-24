package megane6.weplanet.domain.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SignupRequestDto {
	
	@NotBlank(message = "아이디를 입력해주세요.")
	@Pattern(regexp = "^[a-zA-Z0-9]{4,20}$", message = "아이디는 영문/숫자 4~20자로 입력해주세요.")
	private String username;
	
	@NotBlank(message = "비밀번호를 입력해주세요.")
	@Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*[0-9]).{8,20}$", message = "비밀번호는 영문/숫자 포함 8~20자로 입력해주세요.")
	private String password;
	
	@NotBlank(message = "비밀번호 확인을 입력해주세요.")
	private String passwordConfirm;
	
	private String nickname; // 선택 입력 - 비어있으면 자동 생성
	
	@NotBlank(message = "이름을 입력해주세요.")
	private String realName;
	
	@NotBlank(message = "이메일을 입력해주세요.")
	@Email(message = "올바른 이메일 형식이 아닙니다.")
	private String email;
	
	public boolean isPasswordConfirmed() {
		return password != null && password.equals(passwordConfirm);
	}
}