package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.dto.SignupRequestDto;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.email.SignupEmailVerificationService;
import megane6.weplanet.util.NicknameGenerator;
import megane6.weplanet.util.NicknamePolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {
	
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final NicknameGenerator nicknameGenerator;
	private final SignupEmailVerificationService emailVerificationService;
	
	@Transactional
	public User signup(SignupRequestDto dto) {
		if (!dto.isPasswordConfirmed()) {
			throw new IllegalArgumentException("비밀번호와 비밀번호 확인이 일치하지 않습니다.");
		}
		if (userRepository.existsByUsername(dto.getUsername())) {
			throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
		}
		if (userRepository.existsByEmail(dto.getEmail())) {
			throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
		}
		
		String nickname = resolveNickname(dto.getNickname());
		String encodedPassword = passwordEncoder.encode(dto.getPassword());
		
		User user = User.createFan(
				dto.getUsername(),
				encodedPassword,
				dto.getRealName(),
				nickname,
				dto.getEmail()
		);
		
		// 실제 회원가입 이메일 인증을 구현하기 전까지 사용하는 모의 인증 처리
		user.markEmailVerified(LocalDateTime.now());
		
		return userRepository.save(user);
	}
	
	// 회원가입 때 쓰던 것과 같은 비밀번호 정책 (영문/숫자 포함 8~20자)
	private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[a-zA-Z])(?=.*[0-9]).{8,20}$");

	// 회원가입 화면의 "중복 확인" 버튼용 - 실제로 DB를 조회해서 사용 가능 여부를 알려준다.
	public boolean isUsernameAvailable(String username) {
		return !userRepository.existsByUsername(username);
	}

	// [닉네임 관리] 회원정보(마이페이지) 수정 - 아이디는 로그인 식별자라 여기서 바꾸지 않고,
	// 닉네임/이름/이메일/비밀번호(선택 입력 시에만)를 갱신한다.
	// 반환하는 AuthenticatedUser는 호출한 컨트롤러가 SecurityContext를 즉시 갱신할 때 씀 -
	// 안 그러면 세션에 남아있는 예전 닉네임 때문에 재로그인 전까지 헤더가 안 바뀜.
	@Transactional
	public AuthenticatedUser updatePortalAccount(User user, String nickname, String realName, String email,
												  String currentPassword, String newPassword, String confirmPassword) {
		String trimmedNickname = nickname == null ? "" : nickname.trim();
		String trimmedRealName = realName == null ? "" : realName.trim();
		String trimmedEmail = email == null ? "" : email.trim();

		if (trimmedNickname.isBlank() || trimmedRealName.isBlank() || trimmedEmail.isBlank()) {
			throw new IllegalArgumentException("닉네임/이름/이메일은 비워둘 수 없습니다.");
		}

		if (!trimmedNickname.equals(user.getNickname())) {
			if (!NicknamePolicy.isAllowed(trimmedNickname)) {
				throw new IllegalArgumentException("사용할 수 없는 닉네임 형식입니다.");
			}
			if (userRepository.existsByNickname(trimmedNickname)) {
				throw new IllegalArgumentException("이미 사용 중인 닉네임입니다.");
			}
		}
		if (!trimmedEmail.equals(user.getEmail())) {
			// 이메일은 설정 화면에서 잠겨 있고, "수정하기" → 인증코드 발송/확인을 거쳐야만 값이 바뀔 수 있다.
			// 여기서 인증 여부를 한 번 더 검증하는 건, JS를 우회해서 곧바로 폼을 제출하는 경우를 막기 위함.
			if (!emailVerificationService.isVerified(trimmedEmail)) {
				throw new IllegalArgumentException("이메일 인증을 먼저 완료해주세요.");
			}
			if (userRepository.existsByEmail(trimmedEmail)) {
				throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
			}
			user.changePortalProfile(trimmedNickname, trimmedEmail);
			emailVerificationService.clear(trimmedEmail);
		} else {
			user.changePortalProfile(trimmedNickname, user.getEmail());
		}
		user.changeRealName(trimmedRealName);

		// 비밀번호 변경은 currentPassword/newPassword/confirmPassword 중 하나라도 입력됐으면 시도한 것으로 본다.
		// 화면(JS)에서는 현재 비밀번호를 입력해야 새 비밀번호 칸이 열리지만, 서버에서도 한 번 더 검증한다
		// (JS를 우회해서 직접 요청을 보내는 경우를 막기 위함).
		boolean wantsPasswordChange = hasText(currentPassword) || hasText(newPassword) || hasText(confirmPassword);
		if (wantsPasswordChange) {
			if (!hasText(currentPassword) || !passwordEncoder.matches(currentPassword, user.getPassword())) {
				throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
			}
			if (!hasText(newPassword)) {
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

		return AuthenticatedUser.builder()
				.id(user.getId())
				.username(user.getUsername())
				.password(user.getPassword())
				.nickname(user.getNickname())
				.enabled(user.isLoginable())
				.roleName(user.getRole().authority())
				.build();
	}

	// [회원탈퇴] 상태만 WITHDRAWN으로 바꾸는 소프트 삭제 - User.withdraw() 참고.
	@Transactional
	public void withdraw(User user) {
		user.withdraw();
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private String resolveNickname(String requestedNickname) {
		if (requestedNickname == null || requestedNickname.isBlank()) {
			return nicknameGenerator.generate();
		}
		if (!NicknamePolicy.isAllowed(requestedNickname)) {
			throw new IllegalArgumentException("사용할 수 없는 닉네임 형식입니다.");
		}
		if (userRepository.existsByNickname(requestedNickname)) {
			throw new IllegalArgumentException("이미 사용 중인 닉네임입니다.");
		}
		return requestedNickname;
	}
}
