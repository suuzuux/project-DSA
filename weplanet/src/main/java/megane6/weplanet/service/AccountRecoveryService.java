package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

// [아이디 찾기 / 비밀번호 재설정] EmailVerificationService(이메일 인증)와 짝을 이뤄서 쓰는 서비스.
// "이메일 인증이 끝났는지"는 컨트롤러가 EmailVerificationService로 먼저 확인하고,
// 여기서는 실제 계정 조회/변경 로직만 담당한다.
@Service
@RequiredArgsConstructor
public class AccountRecoveryService {
	
	private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[a-zA-Z])(?=.*[0-9]).{8,20}$");
	
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	
	// 아이디 찾기 1단계: 이름+이메일이 실제로 같이 등록된 계정인지 확인 (아무 이메일에나 코드를 보내지 않기 위함)
	public boolean matchesRealNameAndEmail(String realName, String email) {
		return userRepository.findByEmail(email)
				.map(user -> user.getRealName().equals(realName))
				.orElse(false);
	}
	
	// 아이디 찾기 2단계: 이메일 인증까지 끝난 뒤에만 호출됨
	public String findUsernameByEmail(String email) {
		return userRepository.findByEmail(email)
				.map(User::getUsername)
				.orElseThrow(() -> new IllegalArgumentException("일치하는 계정을 찾을 수 없습니다."));
	}
	
	// 비밀번호 재설정 1단계: 아이디+이메일이 같이 등록된 계정인지 확인
	public boolean matchesUsernameAndEmail(String username, String email) {
		return userRepository.findByUsername(username)
				.map(user -> user.getEmail().equals(email))
				.orElse(false);
	}
	
	// 비밀번호 재설정 2단계: 이메일 인증까지 끝난 뒤에만 호출됨
	@Transactional
	public void resetPassword(String username, String email, String newPassword, String confirmPassword) {
		User user = userRepository.findByUsername(username)
				.filter(u -> u.getEmail().equals(email))
				.orElseThrow(() -> new IllegalArgumentException("일치하는 계정을 찾을 수 없습니다."));
		
		if (newPassword == null || newPassword.isBlank()) {
			throw new IllegalArgumentException("새 비밀번호를 입력해주세요.");
		}
		if (!PASSWORD_PATTERN.matcher(newPassword).matches()) {
			throw new IllegalArgumentException("비밀번호는 영문/숫자 포함 8~20자로 입력해주세요.");
		}
		if (!newPassword.equals(confirmPassword)) {
			throw new IllegalArgumentException("새 비밀번호 확인이 일치하지 않습니다.");
		}
		
		user.changePassword(passwordEncoder.encode(newPassword));
	}
}