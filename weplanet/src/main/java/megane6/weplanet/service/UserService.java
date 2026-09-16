package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.dto.SignupRequestDto;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.email.MarketingConsentEmailService;
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
	private final MarketingConsentEmailService marketingConsentEmailService;
	
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
		
		// [설정 - 이벤트·혜택 알림] 가입 화면의 "(선택) 광고 및 마케팅 활용 동의" 체크박스 값을 그대로 반영.
		// 이후 설정 화면의 "광고성 정보 알림 받기" 토글과 같은 값을 공유한다.
		user.changeMarketingConsent(dto.isMarketingConsent());
		
		User saved = userRepository.save(user);
		
		// [광고성 정보 알림] 데모용 - 실제 운영 기능은 아니고, 이 기능이 살아있다는 걸 보여주기 위해
		// 인증된 이메일로 가입 완료 메일을 무조건 1통 보낸다 (광고·마케팅 동의 체크 여부와 무관).
		try {
			marketingConsentEmailService.sendSignupWelcomeEmail(saved, saved.isMarketingConsent());
		} catch (Exception e) {
			log.error("[광고성 정보 알림] 회원가입 환영 메일 발송 실패: user={}", saved.getId(), e);
		}
		
		// 그중 "(선택) 광고 및 마케팅 활용 동의"까지 체크한 사람에게는 곧바로 커뮤니티 가입 유도 메일을
		// 1통 더 보낸다. 체크 안 했으면(기본값) 여기서 끝 - 나중에 설정 화면에서 토글을 켜면 그때 별도로
		// 동의 확인 메일 1통만 보낸다 (아래 updateNotificationPreference 참고).
		if (saved.isMarketingConsent()) {
			try {
				marketingConsentEmailService.sendCommunityInviteEmail(saved);
			} catch (Exception e) {
				log.error("[광고성 정보 알림] 커뮤니티 가입 유도 메일 발송 실패: user={}", saved.getId(), e);
			}
		}
		
		return saved;
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
			// AUTH-10: "연동된 소셜 provider의 이메일이라 못 바꾼다"는 제약을 없앴다 - 이제 연동 여부와
			// 등록 이메일은 서로 독립적인 값이라, 제공자와 무관하게 누구나 인증 절차만 거치면 바꿀 수 있다.
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

		// 비밀번호 변경/등록은 currentPassword/newPassword/confirmPassword 중 하나라도 입력됐으면 시도한 것으로 본다.
		// 화면(JS)에서는 현재 비밀번호를 입력해야 새 비밀번호 칸이 열리지만, 서버에서도 한 번 더 검증한다
		// (JS를 우회해서 직접 요청을 보내는 경우를 막기 위함).
		boolean wantsPasswordChange = hasText(currentPassword) || hasText(newPassword) || hasText(confirmPassword);
		if (wantsPasswordChange) {
			// AUTH-10: provider가 아니라 "지금 비밀번호가 있는지"로 판단한다. 비밀번호가 이미 있는 계정만
			// 현재 비밀번호 확인을 거치고, 비밀번호가 아직 없던 계정(소셜 전용 가입)은 새로 등록하는
			// 것이므로 확인할 현재 비밀번호 자체가 없다 - 이 분기를 건너뛴다.
			if (user.hasPassword()
					&& (!hasText(currentPassword) || !passwordEncoder.matches(currentPassword, user.getPassword()))) {
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

	// [설정 - 이벤트·혜택 알림] type: marketing(광고성 정보) / email(커뮤니티 활동 이메일) / night(야간 알림)
	@Transactional
	public void updateNotificationPreference(User user, String type, boolean enabled) {
		switch (type) {
			case "marketing" -> {
				user.changeMarketingConsent(enabled);
				// [광고성 정보 알림] 데모용 - 가입 때 체크를 안 했거나 소셜 계정으로 가입해서 동의값이
				// 없던 사람이, 여기 설정 화면에서 토글을 켜는 순간(=enabled) 동의 확인 메일 1통만 보낸다.
				// (가입 때 이미 체크해서 signup()에서 메일을 보낸 경우는 이 메서드를 타지 않으므로 안 겹침)
				if (enabled) {
					try {
						marketingConsentEmailService.sendMarketingConsentConfirmedEmail(user);
					} catch (Exception e) {
						log.error("[광고성 정보 알림] 설정 화면 동의 확인 메일 발송 실패: user={}", user.getId(), e);
					}
				}
			}
			case "email" -> user.changeCommunityActivityEmailEnabled(enabled);
			case "night" -> user.changeNightNotificationAllowed(enabled);
			default -> throw new IllegalArgumentException("알 수 없는 알림 종류입니다.");
		}
	}

	// [AUTH-10] 소셜 연동 해제. 비밀번호는 건드리지 않는다 - User.unlinkSocialProvider() 참고.
	// 비밀번호가 없는 계정(소셜 전용 가입)은 이 소셜이 유일한 로그인 수단이라, 해제를 허용하면 계정에
	// 영영 다시 로그인할 수 없게 된다. 설정 화면에서는 이 경우 버튼 자체를 비활성화해두지만, 직접 요청을
	// 보내는 경우까지 막기 위해 여기서도 한 번 더 검증한다.
	@Transactional
	public void unlinkSocialProvider(User user) {
		if (!user.hasPassword()) {
			throw new IllegalArgumentException("비밀번호가 설정되어 있지 않아 연동을 해제할 수 없습니다. 먼저 비밀번호를 설정해주세요.");
		}
		user.unlinkSocialProvider();
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
