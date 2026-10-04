package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.dto.SignupRequestDto;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Language;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.domain.event.AccountMailEvent;
import megane6.weplanet.i18n.PreferredLocaleResolver;
import megane6.weplanet.repository.UserFollowRepository;
import megane6.weplanet.repository.UserRepository;
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
	// 가입 완료·광고성 정보 동의 안내 메일 요청 (AccountMailEvent → AccountMailListener)
	private final ApplicationEventPublisher eventPublisher;
	// [회원탈퇴] 탈퇴 시 가입해둔 커뮤니티/팔로우 관계까지 함께 정리하기 위해 의존한다.
	private final CommunityJoinService communityJoinService;
	private final UserFollowRepository userFollowRepository;

	// 예외 메시지는 메시지 키로 던지고, 화면에 내보내는 컨트롤러가 Messages.resolve(e)로 번역한다 (AuthController, SettingsController).

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
		
		// 가입 화면에서 이메일 인증코드 확인을 마친 경우에만 여기까지 온다 (AuthController.signup 이 먼저 확인) - 인증 완료 시각을 남긴다
		user.markEmailVerified(LocalDateTime.now());
		
		// [설정 - 이벤트·혜택 알림] 가입 화면의 "(선택) 광고 및 마케팅 활용 동의" 체크박스 값을 그대로 반영.
		// 이후 설정 화면의 "광고성 정보 알림 받기" 토글과 같은 값을 공유한다.
		user.changeMarketingConsent(dto.isMarketingConsent());
		
		// 가입 화면에서 쓰던 언어를 계정 선호 언어로 저장한다. 안 하면 기본값(KO)이 남아
		// 환영 메일이 한국어로 가고, 다음 로그인부터 화면도 한국어로 바뀐다.
		user.changePreferredLanguage(PreferredLocaleResolver.toLanguage(LocaleContextHolder.getLocale()));

		// 가입 직후 바로 로그인시키므로(AuthController.signup) 첫 로그인 시각도 함께 남긴다.
		// 폼 로그인이면 LoginSuccessHandler 가 기록하지만 자동 로그인은 그 처리를 거치지 않는다.
		user.recordLogin();

		User saved = userRepository.save(user);

		// 가입 완료 메일 1통(데모용, 동의 여부와 무관) + "(선택) 광고 및 마케팅 활용 동의"까지 했으면 커뮤니티 가입 유도 메일 1통.
		// 메일은 가입이 확정된 뒤 백그라운드로 보낸다 (AccountMailListener).
		eventPublisher.publishEvent(AccountMailEvent.signupWelcome(saved.getId(), saved.isMarketingConsent()));

		return saved;
	}
	
	// 회원가입 때 쓰던 것과 같은 비밀번호 정책 (영문/숫자 포함 8~20자)
	private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[a-zA-Z])(?=.*[0-9]).{8,20}$");
	// 설정 화면 전화번호(선택) - 숫자·하이픈·+ 만, 최대 20자
	private static final Pattern PHONE_PATTERN = Pattern.compile("^[0-9+\\-]{1,20}$");

	// 회원가입 화면의 "중복 확인" 버튼용 - 실제로 DB를 조회해서 사용 가능 여부를 알려준다.
	public boolean isUsernameAvailable(String username) {
		return !userRepository.existsByUsername(username);
	}

	// 회원정보 수정 - 닉네임/이름/이메일/전화번호, 비밀번호(입력했을 때만)를 바꾼다 (아이디는 바꾸지 않음).
	// 반환한 AuthenticatedUser 로 컨트롤러가 SecurityContext 를 바로 갱신한다 (헤더 닉네임이 즉시 바뀌도록).
	@Transactional
	// newEmailVerified: 이 세션에서 이메일 변경 용도로 인증을 마쳤는지
	// emailChangeAuthorized: 이메일 "수정하기"에서 현재 비밀번호 확인을 마쳤는지 (소셜 전용 계정은 항상 true)
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
			// 아티스트(멤버) 닉네임과는 겹쳐도 된다 - 팬 쪽 계정끼리만 중복 검사
			if (userRepository.existsByNicknameAndRoleNotIn(trimmedNickname, Role.ARTIST_SIDE)) {
				throw new IllegalArgumentException("signup.error.nicknameTaken");
			}
		}
		boolean emailChanged = !trimmedEmail.equals(user.getEmail());
		if (emailChanged) {
			// 이메일은 "수정하기" → 인증코드 확인을 거쳐야만 바뀐다 (소셜 연동 여부와 무관).
			// 화면(JS)을 우회해 바로 제출하는 경우를 막으려고 서버에서도 인증 여부를 확인한다.
			if (!newEmailVerified) {
				throw new IllegalArgumentException("signup.error.emailNotVerified");
			}
			// 비밀번호가 있는 계정은 이메일을 바꾸기 전에 현재 비밀번호를 다시 확인한다 (EmailChangeAuthService).
			// 로그인된 브라우저를 잠깐 쓴 사람이 이메일을 바꿔 계정을 가져가는 것을 막기 위함.
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
		// real_name 은 VARBINARY(255)(UTF-8 바이트) - 가입 화면과 같은 50자 제한
		if (trimmedRealName.length() > 50) {
			throw new IllegalArgumentException("signup.validation.realNameTooLong");
		}
		user.changeRealName(trimmedRealName);

		// 전화번호(선택) - 비우면 지운다
		String trimmedPhone = phone == null ? "" : phone.trim();
		if (!trimmedPhone.isEmpty() && !PHONE_PATTERN.matcher(trimmedPhone).matches()) {
			throw new IllegalArgumentException("settings.error.phoneInvalid");
		}
		user.changePhone(trimmedPhone.isEmpty() ? null : trimmedPhone);

		// 비밀번호 칸 중 하나라도 입력했으면 비밀번호 변경/등록을 시도한 것으로 본다.
		// 화면(JS)에서도 막지만, 직접 요청을 보내는 경우를 위해 서버에서 한 번 더 검증한다.
		boolean wantsPasswordChange = hasText(currentPassword) || hasText(newPassword) || hasText(confirmPassword);
		if (wantsPasswordChange) {
			// 비밀번호가 이미 있는 계정만 현재 비밀번호를 확인한다 (소셜 전용 계정은 새로 등록하는 것이라 생략).
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

	// 회원 탈퇴 - 비밀번호가 있는 계정은 현재 비밀번호가 맞아야 한다 (소셜 전용 계정은 생략).
	// 상태 변경·개인정보 익명화는 User.withdraw(), 가입한 커뮤니티와 팔로우 관계는 여기서 정리한다.
	@Transactional
	public void withdraw(User user, String currentPassword) {
		if (user.hasPassword()
				&& (!hasText(currentPassword) || !passwordEncoder.matches(currentPassword, user.getPassword()))) {
			throw new IllegalArgumentException(hasText(currentPassword)
					? "settings.withdraw.passwordMismatch"
					: "settings.withdraw.passwordRequired");
		}
		user.withdraw();

		// 가입한 커뮤니티는 leave() 로 하나씩 탈퇴 처리한다 (프로필·사진·그 커뮤니티의 팔로우까지 정리).
		// 순회 중 삭제로 ConcurrentModificationException 이 나지 않게 목록을 복사해서 돈다.
		for (Long artistId : Set.copyOf(communityJoinService.joinedArtistIds(user))) {
			communityJoinService.leave(user, artistId);
		}

		// 팬→아티스트 팔로우는 커뮤니티 가입 여부와 무관하게 가능했고, 아티스트 본인은 자기 커뮤니티에
		// CommunityMember가 없어 위 루프를 안 타므로, 남아있을 수 있는 팔로우 관계를 방향 상관없이 마저 정리한다.
		userFollowRepository.deleteByFollowerId(user.getId());
		userFollowRepository.deleteByFollowingId(user.getId());
	}

	// [설정 - 이벤트·혜택 알림] type: marketing(광고성 정보) / email(커뮤니티 활동 이메일) / night(야간 알림)
	@Transactional
	public void updateNotificationPreference(User user, String type, boolean enabled) {
		switch (type) {
			case "marketing" -> {
				user.changeMarketingConsent(enabled);
				// 데모용 - 설정 화면에서 광고성 정보 알림을 켜는 순간 동의 확인 메일 1통을 보낸다.
				// 저장이 끝난 뒤 백그라운드로 보내서 토글 응답이 메일 발송을 기다리지 않는다 (AccountMailListener).
				if (enabled) {
					eventPublisher.publishEvent(AccountMailEvent.marketingConsentConfirmed(user.getId()));
				}
			}
			case "email" -> user.changeCommunityActivityEmailEnabled(enabled);
			case "night" -> user.changeNightNotificationAllowed(enabled);
			default -> throw new IllegalArgumentException("error.user.unknownNotifyType");
		}
	}

	// 기본 서비스 언어 저장 - 화면 언어이자 게시글/댓글 AI 번역 대상 언어로 쓰인다.
	@Transactional
	public void updateLanguage(User user, Language language) {
		user.changePreferredLanguage(language);
	}

	// 소셜 연동 해제 (비밀번호는 그대로). 비밀번호가 없는 계정은 이 소셜이 유일한 로그인 수단이라 막는다.
	// 화면에서도 버튼을 막아 두지만, 직접 요청을 보내는 경우를 위해 서버에서 한 번 더 확인한다.
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
		// 아티스트(멤버) 닉네임과는 겹쳐도 된다 - 팬 쪽 계정끼리만 중복 검사
		if (userRepository.existsByNicknameAndRoleNotIn(requestedNickname, Role.ARTIST_SIDE)) {
			throw new IllegalArgumentException("signup.error.nicknameTaken");
		}
		return requestedNickname;
	}
}
