package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.domain.event.AccountMailEvent;
import megane6.weplanet.i18n.PreferredLocaleResolver;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.security.PendingSocialSignup;
import megane6.weplanet.util.NicknameGenerator;
import megane6.weplanet.util.NicknamePolicy;
import megane6.weplanet.util.UsernameGenerator;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** 소셜 계정으로 새 계정을 만든다 (가입 직후 로그인하므로 로그인 시각도 기록). */
@Slf4j
@Service
@RequiredArgsConstructor
public class SocialSignupService {

	private final UserRepository userRepository;
	private final UsernameGenerator usernameGenerator;
	private final NicknameGenerator nicknameGenerator;
	// 가입 완료 메일 이벤트 발행
	private final ApplicationEventPublisher eventPublisher;

	/** 소셜 이메일이 다른 계정에서 쓰이면 메시지 키 예외 (로그인 화면에서 안내). */
	@Transactional
	public User signup(PendingSocialSignup pending, boolean marketingConsent) {
		// 중복 클릭이나 다른 탭에서 이미 가입된 경우 그 계정을 그대로 쓴다.
		Optional<User> already = userRepository.findByProviderAndProviderId(pending.provider(), pending.providerId());
		if (already.isPresent()) {
			already.get().recordLogin();
			return already.get();
		}
		if (userRepository.existsByEmail(pending.email())) {
			throw new IllegalStateException("loginEntry.socialEmailTaken");
		}

		// 소셜 가입은 비밀번호를 만들지 않는다.
		String username = usernameGenerator.generate(pending.provider());
		String nickname = resolveNickname(pending.suggestedNickname());
		User newUser = User.createSocialFan(username, null, pending.realName(), nickname, pending.email(),
				pending.provider(), pending.providerId());
		// 광고·마케팅 동의 (설정 화면 알림 토글과 같은 값)
		newUser.changeMarketingConsent(marketingConsent);
		// 확인 화면 언어를 선호 언어로 저장한다.
		newUser.changePreferredLanguage(PreferredLocaleResolver.toLanguage(LocaleContextHolder.getLocale()));
		User saved = userRepository.save(newUser);
		saved.recordLogin(); // 가입 직후 바로 로그인시키므로 최종 로그인 시각도 함께 남긴다

		// 가입 완료 메일 (동의 시 커뮤니티 가입 유도 메일 추가, 커밋 후 백그라운드).
		eventPublisher.publishEvent(AccountMailEvent.signupWelcome(saved.getId(), marketingConsent));
		return saved;
	}

	// 소셜 이름이 닉네임 규칙을 통과하고 중복이 없으면 쓰고, 아니면 자동 생성한다.
	private String resolveNickname(String suggestedNickname) {
		String candidate = suggestedNickname == null ? "" : suggestedNickname.trim();
		// 팬 쪽 계정끼리만 닉네임 중복 검사
		if (NicknamePolicy.isAllowed(candidate)
				&& !userRepository.existsByNicknameAndRoleNotIn(candidate, Role.ARTIST_SIDE)) {
			return candidate;
		}
		return nicknameGenerator.generate();
	}
}
