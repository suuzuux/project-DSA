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

/**
 * 소셜 계정으로 위플래닛 계정을 새로 만든다 ("가입하시겠습니까?" 확인 화면에서 [예]를 눌렀을 때).
 * 가입 직후 바로 로그인시키므로 최종 로그인 시각도 여기서 남긴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SocialSignupService {

	private final UserRepository userRepository;
	private final UsernameGenerator usernameGenerator;
	private final NicknameGenerator nicknameGenerator;
	// 가입 완료 안내 메일 요청 (AccountMailEvent → AccountMailListener)
	private final ApplicationEventPublisher eventPublisher;

	/**
	 * @throws IllegalStateException 소셜 이메일이 이미 다른 계정에 쓰이고 있는 경우. 메시지는 다른 예외들과 같이 메시지 키이고,
	 *                               컨트롤러는 로그인 화면(/login?socialEmailTaken)에서 같은 키의 안내를 보여준다
	 */
	@Transactional
	public User signup(PendingSocialSignup pending, boolean marketingConsent) {
		// [예]를 두 번 눌렀거나 다른 탭에서 먼저 가입된 경우 - 이미 만들어진 그 계정을 그대로 쓴다
		// (방금 소셜 인증을 통과한 본인의 계정이므로 로그인시켜도 된다)
		Optional<User> already = userRepository.findByProviderAndProviderId(pending.provider(), pending.providerId());
		if (already.isPresent()) {
			already.get().recordLogin();
			return already.get();
		}
		if (userRepository.existsByEmail(pending.email())) {
			throw new IllegalStateException("loginEntry.socialEmailTaken");
		}

		// 소셜 가입은 비밀번호를 만들지 않는다(null). 필요하면 나중에 설정 화면에서 등록한다.
		String username = usernameGenerator.generate(pending.provider());
		String nickname = resolveNickname(pending.suggestedNickname());
		User newUser = User.createSocialFan(username, null, pending.realName(), nickname, pending.email(),
				pending.provider(), pending.providerId());
		// 확인 화면의 "(선택) 광고 및 마케팅 활용 동의" - 아이디 가입과 같은 값(설정 화면 "광고성 정보 알림 받기")
		newUser.changeMarketingConsent(marketingConsent);
		// 확인 화면에서 쓰던 언어를 계정 선호 언어로 저장 (아이디 가입 UserService.signup 과 같은 규칙)
		newUser.changePreferredLanguage(PreferredLocaleResolver.toLanguage(LocaleContextHolder.getLocale()));
		User saved = userRepository.save(newUser);
		saved.recordLogin(); // 가입 직후 바로 로그인시키므로 최종 로그인 시각도 함께 남긴다

		// 아이디 가입과 같은 규칙: 가입 완료 메일 1통 + 마케팅에 동의했으면 커뮤니티 가입 유도 메일 1통.
		// 가입이 확정된 뒤 백그라운드로 보내고, 받을 수 없는 주소(카카오/LINE)는 AccountMailListener 가 거른다.
		eventPublisher.publishEvent(AccountMailEvent.signupWelcome(saved.getId(), marketingConsent));
		return saved;
	}

	// 소셜 이름(구글 이름, 카카오/LINE 닉네임)이 닉네임 규칙(NicknamePolicy)을 통과하고 다른 팬이 안 쓰면 그대로 쓴다.
	// 아니면 자동 생성 닉네임을 쓴다 (설정 화면에서 바꿀 수 있다).
	private String resolveNickname(String suggestedNickname) {
		String candidate = suggestedNickname == null ? "" : suggestedNickname.trim();
		// 아티스트(멤버) 닉네임과는 겹쳐도 된다 - 팬 쪽 계정끼리만 중복 검사
		if (NicknamePolicy.isAllowed(candidate)
				&& !userRepository.existsByNicknameAndRoleNotIn(candidate, Role.ARTIST_SIDE)) {
			return candidate;
		}
		return nicknameGenerator.generate();
	}
}
