package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.i18n.PreferredLocaleResolver;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.security.PendingSocialSignup;
import megane6.weplanet.service.email.MarketingConsentEmailService;
import megane6.weplanet.util.NicknameGenerator;
import megane6.weplanet.util.UsernameGenerator;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 소셜 계정으로 위플래닛 계정을 새로 만든다. "가입하시겠습니까?" 확인 화면에서 [예]를 눌렀을 때 호출된다.
 * 가입 직후 바로 로그인시키므로 최종 로그인 시각(recordLogin)도 여기서 남긴다.
 * (예전에는 OAuth2LoginSuccessHandler.createNewSocialUser 에서 소셜 인증 직후 바로 만들었다)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SocialSignupService {

	private final UserRepository userRepository;
	private final UsernameGenerator usernameGenerator;
	private final NicknameGenerator nicknameGenerator;
	private final MarketingConsentEmailService marketingConsentEmailService;

	/**
	 * @throws IllegalStateException 소셜 이메일이 이미 다른 계정에 쓰이고 있는 경우 (화면에 그대로 보여줄 문구)
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
			throw new IllegalStateException("이미 가입된 이메일입니다. 먼저 로그인한 뒤, 설정 화면에서 소셜 계정을 연동해주세요.");
		}

		// AUTH-10: 신규 소셜 가입은 비밀번호를 만들지 않는다(null). 필요하면 나중에 설정 화면에서 이름/이메일/비밀번호를 고친다.
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

		// [광고성 정보 알림] 아이디 가입(UserService.signup)과 같은 규칙: 가입 완료 메일은 항상 1통,
		// 마케팅에 동의했으면 커뮤니티 가입 유도 메일 1통 더. 카카오/LINE 은 받을 수 없는 주소라 보내지 않는다.
		if (!saved.hasPlaceholderEmail()) {
			try {
				marketingConsentEmailService.sendSignupWelcomeEmail(saved, marketingConsent);
			} catch (Exception e) {
				log.error("[광고성 정보 알림] 소셜 회원가입 환영 메일 발송 실패: user={}", saved.getId(), e);
			}
			if (marketingConsent) {
				try {
					marketingConsentEmailService.sendCommunityInviteEmail(saved);
				} catch (Exception e) {
					log.error("[광고성 정보 알림] 소셜 회원가입 커뮤니티 가입 유도 메일 발송 실패: user={}", saved.getId(), e);
				}
			}
		}
		return saved;
	}

	private String resolveNickname(String suggestedNickname) {
		// 아티스트(멤버) 닉네임과는 겹쳐도 된다 - 팬 쪽 계정끼리만 중복 검사
		if (suggestedNickname != null && !suggestedNickname.isBlank()
				&& !userRepository.existsByNicknameAndRoleNotIn(suggestedNickname, Role.ARTIST_SIDE)) {
			return suggestedNickname;
		}
		return nicknameGenerator.generate();
	}
}
