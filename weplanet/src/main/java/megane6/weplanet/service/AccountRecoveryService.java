package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.regex.Pattern;

// 아이디 찾기 / 비밀번호 재설정의 계정 조회·변경 담당 (이메일 인증 여부는 컨트롤러가 먼저 확인).
// 대상은 비밀번호가 있고, 메일을 받을 수 있는 주소가 등록된 계정이다 (isEligibleForRecovery).
@Service
@RequiredArgsConstructor
public class AccountRecoveryService {
	
	private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[a-zA-Z])(?=.*[0-9])(?=.*[^a-zA-Z0-9])[!-~]{8,20}$");
	
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	// 예외 메시지는 메시지 키로 던지고, 화면에 내보내는 컨트롤러가 Messages.resolve(e)로 번역한다.

	// 아이디 찾기 1단계: 이름+이메일이 같이 등록된 계정인지 확인 (아무 이메일에나 코드를 보내지 않기 위함).
	// 실제로 코드를 보낼 수 있는지는 isEligibleForRecovery() 에서 따로 본다.
	public boolean matchesRealNameAndEmail(String realName, String email) {
		return userRepository.findByEmail(email)
				.map(user -> user.getRealName().equals(realName))
				.orElse(false);
	}
	
	// 아이디 찾기 2단계: 이메일 인증까지 끝난 뒤에만 호출됨
	public String findUsernameByEmail(String email) {
		return userRepository.findByEmail(email)
				.map(User::getUsername)
				.orElseThrow(() -> new IllegalArgumentException("error.user.accountNotFound"));
	}

	// 비밀번호 재설정 1단계: 아이디+이메일이 같이 등록된 계정인지 확인
	public boolean matchesUsernameAndEmail(String username, String email) {
		return userRepository.findByUsername(username)
				.map(user -> sameEmail(user.getEmail(), email))
				.orElse(false);
	}

	// 인증코드를 보낼 주소 = 가입 때 등록한 주소 (일치하는 계정이 없거나 복구 대상이 아니면 empty).
	// 대소문자만 다르게 입력해도(Kwon@ / kwon@) 코드는 항상 계정 주인의 메일함으로 간다.
	public Optional<String> findIdRecipient(String realName, String email) {
		return userRepository.findByEmail(email)
				.filter(user -> user.getRealName().equals(realName))
				.filter(this::isEligibleForRecovery)
				.map(User::getEmail);
	}

	public Optional<String> resetPasswordRecipient(String username, String email) {
		return userRepository.findByUsername(username)
				.filter(user -> sameEmail(user.getEmail(), email))
				.filter(this::isEligibleForRecovery)
				.map(User::getEmail);
	}

	// 계정 복구(아이디/비밀번호 찾기) 대상인지 - 비밀번호가 있고,
	// 받을 수 없는 시스템 주소(카카오/LINE 가입 때 만든 *.weplanet.local)가 아닌 계정만 대상이다.
	public boolean isEligibleForRecovery(String email) {
		return userRepository.findByEmail(email).map(this::isEligibleForRecovery).orElse(false);
	}

	// 비밀번호 재설정 2단계: 이메일 인증까지 끝난 뒤에만 호출됨. 비밀번호를 바꾼 계정을 돌려준다
	// (컨트롤러가 그 계정의 기존 로그인 세션을 끊는 데 쓴다)
	@Transactional
	public User resetPassword(String username, String email, String newPassword, String confirmPassword) {
		User user = userRepository.findByUsername(username)
				.filter(u -> sameEmail(u.getEmail(), email))
				.filter(this::isEligibleForRecovery)
				.orElseThrow(() -> new IllegalArgumentException("resetPassword.accountNotFound"));

		if (newPassword == null || newPassword.isBlank()) {
			throw new IllegalArgumentException("resetPassword.newPasswordRequired");
		}
		if (!PASSWORD_PATTERN.matcher(newPassword).matches()) {
			throw new IllegalArgumentException("resetPassword.passwordFormatInvalid");
		}
		if (!newPassword.equals(confirmPassword)) {
			throw new IllegalArgumentException("resetPassword.confirmMismatch");
		}
		
		user.changePassword(passwordEncoder.encode(newPassword));
		return user;
	}

	// 이메일은 대소문자를 구분하지 않는다 (가입 중복 확인·아이디 찾기·인증코드 확인과 같은 기준).
	private static boolean sameEmail(String registered, String input) {
		return registered != null && input != null && registered.trim().equalsIgnoreCase(input.trim());
	}

	// 받을 수 없는 시스템 주소(*.weplanet.local)인지는 User.hasPlaceholderEmail() 로 본다 - 휴면 배치·알림 메일과 같은 기준.
	private boolean isEligibleForRecovery(User user) {
		return user.hasPassword() && !user.hasPlaceholderEmail();
	}
}
