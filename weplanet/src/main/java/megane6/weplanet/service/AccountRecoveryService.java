package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.regex.Pattern;

// 아이디 찾기·비밀번호 재설정 계정 조회·변경 (비밀번호와 수신 가능한 이메일이 있는 계정 대상).
@Service
@RequiredArgsConstructor
public class AccountRecoveryService {
	
	private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[a-zA-Z])(?=.*[0-9]).{8,20}$");
	
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	// 예외는 메시지 키로 던지고 컨트롤러가 번역한다.

	// 아이디 찾기 1단계: 이름과 이메일이 함께 등록된 계정인지 확인한다.
	public boolean matchesRealNameAndEmail(String realName, String email) {
		return userRepository.findByEmail(email)
				.map(user -> user.getRealName().equals(realName))
				.orElse(false);
	}
	
	// 아이디 찾기 2단계 (이메일 인증 후 호출)
	public String findUsernameByEmail(String email) {
		return userRepository.findByEmail(email)
				.map(User::getUsername)
				.orElseThrow(() -> new IllegalArgumentException("error.user.accountNotFound"));
	}

	// 비밀번호 재설정 1단계: 아이디와 이메일이 함께 등록된 계정인지 확인
	public boolean matchesUsernameAndEmail(String username, String email) {
		return userRepository.findByUsername(username)
				.map(user -> sameEmail(user.getEmail(), email))
				.orElse(false);
	}

	// 인증코드 수신 주소 = 가입 이메일 (대상이 아니면 empty, 대소문자 무시).
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

	// 복구 대상인지 - 비밀번호가 있고 수신 가능한 이메일인 계정만.
	public boolean isEligibleForRecovery(String email) {
		return userRepository.findByEmail(email).map(this::isEligibleForRecovery).orElse(false);
	}

	// 비밀번호 재설정 2단계 - 바꾼 계정을 돌려준다 (기존 세션 종료용).
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

	// 이메일은 대소문자를 구분하지 않는다.
	private static boolean sameEmail(String registered, String input) {
		return registered != null && input != null && registered.trim().equalsIgnoreCase(input.trim());
	}

	// 시스템 주소(*.weplanet.local) 여부는 User.hasPlaceholderEmail() 로 판단한다.
	private boolean isEligibleForRecovery(User user) {
		return user.hasPassword() && !user.hasPlaceholderEmail();
	}
}
