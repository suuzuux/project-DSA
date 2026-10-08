package megane6.weplanet.service.account;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.dto.SignupRequestDto;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Language;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.domain.event.AccountMailEvent;
import megane6.weplanet.i18n.PreferredLocaleResolver;
import megane6.weplanet.repository.fan.UserFollowRepository;
import megane6.weplanet.repository.main.UserRepository;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.community.CommunityJoinService;
import megane6.weplanet.util.NicknameGenerator;
import megane6.weplanet.util.NicknamePolicy;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {
	
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final NicknameGenerator nicknameGenerator;
	// 계정 안내 메일 이벤트 발행
	private final ApplicationEventPublisher eventPublisher;
	// 회원 탈퇴 시 커뮤니티·팔로우 정리용
	private final CommunityJoinService communityJoinService;
	private final UserFollowRepository userFollowRepository;

	// 예외는 메시지 키로 던지고 컨트롤러가 번역한다.

	@Transactional
	public User signup(SignupRequestDto dto) {
		if (!dto.isPasswordConfirmed()) {
			throw new IllegalArgumentException("signup.error.passwordMismatch");
		}
		if (userRepository.existsByUsername(dto.getUsername())) {
			throw new IllegalArgumentException("signup.error.usernameTaken");
		}
		if (userRepository.existsByEmail(dto.getEmail())) {
			throw new IllegalArgumentException("signup.error.emailTaken");
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
		
		// 이메일 인증을 마친 경우에만 오므로 인증 완료 시각을 남긴다.
		user.markEmailVerified(LocalDateTime.now());
		
		// 가입 화면의 광고·마케팅 동의 (설정 토글과 같은 값)
		user.changeMarketingConsent(dto.isMarketingConsent());
		
		// 가입 화면 언어를 선호 언어로 저장한다.
		user.changePreferredLanguage(PreferredLocaleResolver.toLanguage(LocaleContextHolder.getLocale()));

		// 가입 직후 자동 로그인하므로 첫 로그인 시각도 남긴다.
		user.recordLogin();

		User saved = userRepository.save(user);

		// 가입 완료 메일 (동의 시 커뮤니티 가입 유도 메일 추가, 커밋 후 백그라운드).
		eventPublisher.publishEvent(AccountMailEvent.signupWelcome(saved.getId(), saved.isMarketingConsent()));

		return saved;
	}
	
	// 비밀번호 규칙 (영문·숫자 포함 8~20자)
	private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[a-zA-Z])(?=.*[0-9]).{8,20}$");
	// 전화번호 (숫자·하이픈·+, 최대 20자)
	private static final Pattern PHONE_PATTERN = Pattern.compile("^[0-9+\\-]{1,20}$");

	// 아이디 중복 확인
	public boolean isUsernameAvailable(String username) {
		return !userRepository.existsByUsername(username);
	}

	// 회원정보 수정 (아이디 제외) - 반환값으로 SecurityContext 를 바로 갱신한다.
	@Transactional
	// newEmailVerified: 이메일 변경 인증 완료 / emailChangeAuthorized: 현재 비밀번호 확인 완료
	public AuthenticatedUser updatePortalAccount(User user, String nickname, String realName, String email,
												  boolean newEmailVerified, boolean emailChangeAuthorized, String phone,
												  String currentPassword, String newPassword, String confirmPassword) {
		String trimmedNickname = nickname == null ? "" : nickname.trim();
		String trimmedRealName = realName == null ? "" : realName.trim();
		String trimmedEmail = email == null ? "" : email.trim();

		if (trimmedNickname.isBlank() || trimmedRealName.isBlank() || trimmedEmail.isBlank()) {
			throw new IllegalArgumentException("error.user.requiredFields");
		}

		if (!trimmedNickname.equals(user.getNickname())) {
			if (!NicknamePolicy.isAllowed(trimmedNickname)) {
				throw new IllegalArgumentException("signup.error.nicknameInvalid");
			}
			// 팬 쪽 계정끼리만 닉네임 중복 검사
			if (userRepository.existsByNicknameAndRoleNotIn(trimmedNickname, Role.ARTIST_SIDE)) {
				throw new IllegalArgumentException("signup.error.nicknameTaken");
			}
		}
		boolean emailChanged = !trimmedEmail.equals(user.getEmail());
		if (emailChanged) {
			// 이메일은 인증코드 확인을 거쳐야만 바뀐다 (서버에서도 확인).
			if (!newEmailVerified) {
				throw new IllegalArgumentException("signup.error.emailNotVerified");
			}
			// 비밀번호가 있는 계정은 이메일 변경 전에 비밀번호를 다시 확인한다.
			if (!emailChangeAuthorized) {
				throw new IllegalArgumentException("settings.email.passwordCheckRequired");
			}
			if (userRepository.existsByEmail(trimmedEmail)) {
				throw new IllegalArgumentException("settings.email.alreadyInUse");
			}
			user.changePortalProfile(trimmedNickname, trimmedEmail);
		} else {
			user.changePortalProfile(trimmedNickname, user.getEmail());
		}
		// real_name 은 VARBINARY(255) 라 50자 제한
		if (trimmedRealName.length() > 50) {
			throw new IllegalArgumentException("signup.validation.realNameTooLong");
		}
		user.changeRealName(trimmedRealName);

		// 전화번호 (비우면 삭제)
		String trimmedPhone = phone == null ? "" : phone.trim();
		if (!trimmedPhone.isEmpty() && !PHONE_PATTERN.matcher(trimmedPhone).matches()) {
			throw new IllegalArgumentException("settings.error.phoneInvalid");
		}
		user.changePhone(trimmedPhone.isEmpty() ? null : trimmedPhone);

		// 비밀번호 칸을 하나라도 입력했으면 변경 시도로 본다 (서버에서도 검증).
		boolean wantsPasswordChange = hasText(currentPassword) || hasText(newPassword) || hasText(confirmPassword);
		if (wantsPasswordChange) {
			// 비밀번호가 있는 계정만 현재 비밀번호를 확인한다.
			if (user.hasPassword()
					&& (!hasText(currentPassword) || !passwordEncoder.matches(currentPassword, user.getPassword()))) {
				throw new IllegalArgumentException("error.user.currentPasswordMismatch");
			}
			if (!hasText(newPassword)) {
				throw new IllegalArgumentException("error.user.newPasswordRequired");
			}
			if (!PASSWORD_PATTERN.matcher(newPassword).matches()) {
				throw new IllegalArgumentException("signup.validation.passwordPattern");
			}
			if (!newPassword.equals(confirmPassword)) {
				throw new IllegalArgumentException("settings.modal.passwordMismatch");
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

	// 회원 탈퇴 - 비밀번호 확인 후 익명화하고 커뮤니티·팔로우를 정리한다.
	@Transactional
	public void withdraw(User user, String currentPassword) {
		if (user.hasPassword()
				&& (!hasText(currentPassword) || !passwordEncoder.matches(currentPassword, user.getPassword()))) {
			throw new IllegalArgumentException(hasText(currentPassword)
					? "settings.withdraw.passwordMismatch"
					: "settings.withdraw.passwordRequired");
		}
		user.withdraw();

		// 가입 커뮤니티를 하나씩 탈퇴 처리한다 (목록을 복사해 순회).
		for (Long artistId : Set.copyOf(communityJoinService.joinedArtistIds(user))) {
			communityJoinService.leave(user, artistId);
		}

		// 남아 있을 수 있는 팔로우 관계를 방향과 상관없이 정리한다.
		userFollowRepository.deleteByFollowerId(user.getId());
		userFollowRepository.deleteByFollowingId(user.getId());
	}

	// 알림 설정 - marketing / email / night
	@Transactional
	public void updateNotificationPreference(User user, String type, boolean enabled) {
		switch (type) {
			case "marketing" -> {
				user.changeMarketingConsent(enabled);
				// 광고성 알림을 켜면 동의 확인 메일을 백그라운드로 보낸다.
				if (enabled) {
					eventPublisher.publishEvent(AccountMailEvent.marketingConsentConfirmed(user.getId()));
				}
			}
			case "email" -> user.changeCommunityActivityEmailEnabled(enabled);
			case "night" -> user.changeNightNotificationAllowed(enabled);
			default -> throw new IllegalArgumentException("error.user.unknownNotifyType");
		}
	}

	// 기본 서비스 언어 저장 (AI 번역 대상 언어로도 사용)
	@Transactional
	public void updateLanguage(User user, Language language) {
		user.changePreferredLanguage(language);
	}

	// 소셜 연동 해제 - 비밀번호가 없으면 유일한 로그인 수단이라 막는다.
	@Transactional
	public void unlinkSocialProvider(User user) {
		if (!user.hasPassword()) {
			throw new IllegalArgumentException("error.user.cannotUnlinkWithoutPassword");
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
			throw new IllegalArgumentException("signup.error.nicknameInvalid");
		}
		// 팬 쪽 계정끼리만 닉네임 중복 검사
		if (userRepository.existsByNicknameAndRoleNotIn(requestedNickname, Role.ARTIST_SIDE)) {
			throw new IllegalArgumentException("signup.error.nicknameTaken");
		}
		return requestedNickname;
	}
}
