package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.AuthProvider;
import megane6.weplanet.repository.UserRepository;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

// [아이디 찾기 / 비밀번호 재설정] SignupEmailVerificationService(이메일 인증)와 짝을 이뤄서 쓰는 서비스.
// "이메일 인증이 끝났는지"는 컨트롤러가 EmailVerificationService로 먼저 확인하고,
// 여기서는 실제 계정 조회/변경 로직만 담당한다.
//
// AUTH-10: 이제 로컬 비밀번호와 소셜 연동이 동시에 있을 수 있어서, "provider가 LOCAL인지"로는 더 이상
// 대상 여부를 판단할 수 없다. 대신 isEligibleForRecovery()가 "비밀번호가 설정돼 있는지" + "등록된 이메일이
// 가입 시 자동 생성된 가짜 placeholder 주소가 아닌지" 두 가지를 본다. 카카오/LINE 가입자가 나중에 설정에서
// 비밀번호를 등록해도, 이메일이 placeholder 그대로면(인증코드를 받을 수 없으므로) 계속 대상에서 제외된다.
@Service
@RequiredArgsConstructor
public class AccountRecoveryService {
	
	private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[a-zA-Z])(?=.*[0-9]).{8,20}$");
	
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final MessageSource messageSource;

	// SETTINGS-03: 비밀번호 재설정(reset-password.html) 화면에서 실제로 화면에 노출되는 예외 메시지만
	// 현재 세션 로케일로 번역한다.
	private String msg(String code) {
		return messageSource.getMessage(code, null, LocaleContextHolder.getLocale());
	}

	// 아이디 찾기 1단계: 이름+이메일이 실제로 같이 등록된 계정인지 확인 (아무 이메일에나 코드를 보내지 않기 위함).
	// 신원 매칭 자체는 provider/비밀번호 여부와 무관하게 본다 - 계정 존재 여부를 흘리지 않기 위해 대상이
	// 될 수 없는 계정도 일단 "일치"로 취급하고, 실제 코드 발송 가능 여부는 isEligibleForRecovery()에서 따로 본다.
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
				.map(user -> user.getEmail().equals(email))
				.orElse(false);
	}

	// 이메일로 조회되는 계정이 실제로 계정 복구(아이디/비밀번호 찾기) 대상이 될 수 있는지.
	// - 비밀번호가 아예 없는 계정(소셜 전용, 아직 비밀번호를 등록 안 함)은 대상이 아님.
	// - 카카오/LINE 가입 시 자동 생성된 placeholder 이메일(예: kakao_고유ID@kakao.weplanet.local)은
	//   실제로 받을 수 있는 주소가 아니라서, 비밀번호가 있어도 이 이메일 그대로면 대상이 아님.
	public boolean isEligibleForRecovery(String email) {
		return userRepository.findByEmail(email).map(this::isEligibleForRecovery).orElse(false);
	}

	// 비밀번호 재설정 2단계: 이메일 인증까지 끝난 뒤에만 호출됨. 비밀번호를 바꾼 계정을 돌려준다
	// (컨트롤러가 그 계정의 기존 로그인 세션을 끊는 데 쓴다)
	@Transactional
	public User resetPassword(String username, String email, String newPassword, String confirmPassword) {
		User user = userRepository.findByUsername(username)
				.filter(u -> u.getEmail().equals(email))
				.filter(this::isEligibleForRecovery)
				.orElseThrow(() -> new IllegalArgumentException(msg("resetPassword.accountNotFound")));
		
		if (newPassword == null || newPassword.isBlank()) {
			throw new IllegalArgumentException(msg("resetPassword.newPasswordRequired"));
		}
		if (!PASSWORD_PATTERN.matcher(newPassword).matches()) {
			throw new IllegalArgumentException(msg("resetPassword.passwordFormatInvalid"));
		}
		if (!newPassword.equals(confirmPassword)) {
			throw new IllegalArgumentException(msg("resetPassword.confirmMismatch"));
		}
		
		user.changePassword(passwordEncoder.encode(newPassword));
		return user;
	}

	private boolean isEligibleForRecovery(User user) {
		return user.hasPassword() && !isPlaceholderEmail(user);
	}

	private boolean isPlaceholderEmail(User user) {
		if (user.getProvider() == AuthProvider.KAKAO) {
			return ("kakao_" + user.getProviderId() + "@kakao.weplanet.local").equals(user.getEmail());
		}
		if (user.getProvider() == AuthProvider.LINE) {
			return ("line_" + user.getProviderId() + "@line.weplanet.local").equals(user.getEmail());
		}
		return false;
	}
}
