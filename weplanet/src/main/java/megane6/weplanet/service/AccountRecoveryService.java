package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.regex.Pattern;

// [아이디 찾기 / 비밀번호 재설정] SignupEmailVerificationService(이메일 인증)와 짝을 이뤄서 쓰는 서비스.
// "이메일 인증이 끝났는지"는 컨트롤러가 SignupEmailVerificationService로 먼저 확인하고,
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

	// 예외 메시지는 다른 서비스들처럼 메시지 키로 던지고, 화면에 내보내는 컨트롤러가 Messages.resolve(e)로 번역한다.
	// (예전에는 여기서만 번역된 문장을 던졌다 - 화면에 보이는 결과는 같다)

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
				.map(user -> sameEmail(user.getEmail(), email))
				.orElse(false);
	}

	// 인증코드를 실제로 보낼 주소 = 가입 때 등록한 주소. 일치하는 계정이 없거나 복구 대상이 아니면 empty.
	// 입력한 주소가 아니라 등록된 주소로 보낸다 - 대소문자(Kwon@ / kwon@)만 다르게 입력해도 코드는 항상
	// 계정 주인의 메일함으로 간다.
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

	// 이메일은 대소문자를 구분하지 않는다 - 가입 중복 확인·아이디 찾기(DB 조회)·인증코드 확인이 모두 그렇게 동작하는데,
	// 예전에는 비밀번호 찾기만 equals 로 비교해서 Kwon@Gmail.com 으로 가입한 사람이 kwon@gmail.com 을 넣으면
	// "일치하면 보냈습니다"만 뜨고 메일이 오지 않았다.
	private static boolean sameEmail(String registered, String input) {
		return registered != null && input != null && registered.trim().equalsIgnoreCase(input.trim());
	}

	// 받을 수 없는 시스템 주소(*.weplanet.local)인지는 User.hasPlaceholderEmail() 하나로 본다 - 휴면 배치·알림 메일과 같은 기준.
	// (예전에는 여기서만 카카오/LINE 주소 형식을 따로 만들어 비교했다)
	private boolean isEligibleForRecovery(User user) {
		return user.hasPassword() && !user.hasPlaceholderEmail();
	}
}
