package megane6.weplanet.security;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.i18n.PreferredLocaleResolver;
import megane6.weplanet.repository.GroupMemberRepository;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.service.portal.AgencyEnrollmentService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.LocaleResolver;

import java.io.IOException;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class LoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

	private final UserRepository userRepository;
	private final AgencyEnrollmentService agencyEnrollmentService;
	private final LocaleResolver localeResolver;
	private final GroupMemberRepository groupMemberRepository;
	private final LoginAttemptService loginAttemptService;

	@PostConstruct
	public void init() {
		setDefaultTargetUrl("/");
		setAlwaysUseDefaultTargetUrl(true);
	}

	@Override
	@Transactional
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
										Authentication authentication) throws IOException, ServletException {
		AuthenticatedUser principal = (AuthenticatedUser) authentication.getPrincipal();
		// 비밀번호가 맞았으므로 그동안 틀린 횟수를 지운다 (LoginAttemptService - 로그인 비밀번호 대입 방어)
		loginAttemptService.recordSuccess(principal.getUsername());
		boolean portalLogin = "true".equals(request.getParameter("portalLogin"));
		boolean adminLogin = "true".equals(request.getParameter("adminLogin"));
		// 포털(아티스트/에이전시) 로그인 화면에서 직접 고른 언어. 아래 clearAuthentication 이 세션을 버리기 전에
		// 미리 읽어 둔다. 팬 로그인은 지금처럼 계정에 저장된 선호 언어를 따르고, 관리자는 항상 한국어다.
		Locale chosenLocale = portalLogin && PreferredLocaleResolver.hasExplicitChoice(request)
				? localeResolver.resolveLocale(request)
				: null;

		// AUTH-11: 관리자 로그인 화면(adminLogin=true)은 ADMIN 계정만 통과시킨다.
		// 예전에는 이 파라미터가 있으면 역할 검사를 아예 건너뛰어서, 아티스트 그룹 계정이 관리자 로그인 화면으로
		// 들어오면 멤버 프로필 선택 없이 그룹 계정으로 로그인되고 팬/포털 로그인 분리도 우회됐다.
		// 역할 정보는 노출하지 않고, 일반 로그인 실패와 같은 화면으로 보낸다.
		if (adminLogin) {
			if (!"ROLE_ADMIN".equals(principal.getRoleName())) {
				clearAuthentication(request);
				getRedirectStrategy().sendRedirect(request, response, "/admin/login?error");
				return;
			}
		} else if (portalLogin) {
			// 포털 로그인: 아티스트/에이전시 전용. 선택 탭과 실제 역할이 일치해야 함.
			String roleName = principal.getRoleName();

			// 팬·관리자 등 포털 대상이 아닌 계정 → 메인 팬 로그인으로
			if (!"ROLE_ARTIST".equals(roleName) && !"ROLE_AGENCY".equals(roleName)) {
				clearAuthentication(request);
				getRedirectStrategy().sendRedirect(request, response, "/login?error=portal");
				return;
			}

			String expected = expectedPortalRole(request.getParameter("portalRole"));
			if (expected != null && !expected.equals(roleName)) {
				clearAuthentication(request);
				String tab = "ROLE_ARTIST".equals(expected) ? "ARTIST" : "AGENCY";
				getRedirectStrategy().sendRedirect(request, response,
						"/portal/login?error=role&role=" + tab);
				return;
			}
		} else {
			// 일반 팬 로그인(/login, /login/id): 아티스트·에이전시·관리자 계정 차단
			// (AUTH-11: 관리자도 관리자 로그인 화면(/admin/login)으로만 로그인하도록 ROLE_ADMIN 추가)
			// 역할 정보는 노출하지 않고, 일반 로그인 실패와 동일한 화면으로 보낸다.
			String roleName = principal.getRoleName();
			if ("ROLE_ARTIST".equals(roleName) || "ROLE_AGENCY".equals(roleName)
					|| "ROLE_ARTIST_MEMBER".equals(roleName) || "ROLE_ADMIN".equals(roleName)) {
				clearAuthentication(request);
				getRedirectStrategy().sendRedirect(request, response, "/login/id?error");
				return;
			}
		}
		
		// 멤버가 있는 그룹 계정: 여기서는 로그인시키지 않고 프로필 선택(2단계)으로 보낸다.
		// clearAuthentication 이 세션을 통째로 버리고 새 세션을 만들기 때문에,
		// 방금 저장된 그룹 로그인 정보는 사라지고 "대기 그룹 id" 만 새 세션에 남는다.
		// AUTH-11: 로그인 경로(portalLogin 여부)와 관계없이 항상 적용 - 어떤 화면으로 들어오든 그룹 비밀번호만으로는
		// 활동할 수 없고 반드시 멤버 프로필을 고르게 한다.
		if ("ROLE_ARTIST".equals(principal.getRoleName())
				&& groupMemberRepository.existsByGroupIdAndLeftAtIsNull(principal.getId())) {
			clearAuthentication(request);
			ArtistProfileLoginSupport.begin(request.getSession(true), principal.getId());
			// 세션을 새로 만들면서 로케일도 사라지므로 다시 넣는다. 로그인 화면에서 고른 언어가 있으면 그 언어를
			// 프로필 선택 단계까지 이어서 넘기고(멤버 계정에 저장은 ArtistProfileLoginController 가 한다),
			// 없으면 그룹 계정의 선호 언어로 보여준다.
			if (chosenLocale != null) {
				localeResolver.setLocale(request, response, chosenLocale);
				PreferredLocaleResolver.markExplicitChoice(request);
			} else {
				PreferredLocaleResolver.clearExplicitChoice(request);
				userRepository.findOneById(principal.getId()).ifPresent(group ->
						localeResolver.setLocale(request, response, PreferredLocaleResolver.toLocale(group.getPreferredLanguage())));
			}
			getRedirectStrategy().sendRedirect(request, response, "/portal/profiles");
			return;
		}

		userRepository.findOneById(principal.getId()).ifPresent(user -> {
			// 소속사 자동 가입을 로그인 기록(recordLogin)보다 먼저 한다.
			// 커뮤니티 자동 가입(CommunityJoinService.ensureJoined)은 별도 트랜잭션(REQUIRES_NEW)이라,
			// 이 트랜잭션이 먼저 users 행을 수정(recordLogin)해 두면 그 행 잠금 때문에 가입 INSERT(FK 확인)가
			// 잠금이 풀리기를 기다리고, 이 트랜잭션은 가입이 끝나기를 기다려서 로그인이 멈췄다(약 50초 후 실패).
			if (user.getRole() == Role.AGENCY) {
				agencyEnrollmentService.enrollManagedArtists(user);
			}
			user.recordLogin();
			if (user.getRole() == Role.ADMIN) {
				// 관리자는 한국어 고정
				localeResolver.setLocale(request, response, Locale.KOREAN);
			} else if (chosenLocale != null) {
				// 아티스트/에이전시: 포털 로그인 화면에서 고른 언어를 유지하고 계정 선호 언어로 저장한다.
				// (포털 화면에는 로그인 후 언어 메뉴가 없어서, 예전엔 아래 DB 값(대부분 KO)으로 덮어써져
				//  로그인 화면에서만 언어가 바뀌고 로그인 후엔 계속 한국어로 나왔다)
				user.changePreferredLanguage(PreferredLocaleResolver.toLanguage(chosenLocale));
				localeResolver.setLocale(request, response, chosenLocale);
				PreferredLocaleResolver.clearExplicitChoice(request);
			} else {
				// SETTINGS-03 로케일 버그#2 수정: 세션 로케일은 기본값(한국어)로 시작해서, DB에 저장된
				// 선호 언어를 골라도 재로그인 전까지는 화면이 계속 한국어로 나왔다.
				localeResolver.setLocale(request, response, PreferredLocaleResolver.toLocale(user.getPreferredLanguage()));
			}
		});

		getRedirectStrategy().sendRedirect(request, response, RoleHomeRedirects.pathFor(principal));
	}

	/**
	 * 인증 해제 후 새 세션을 열어 invalidSessionUrl(/login?expired)로 튕기지 않게 함.
	 * 화면 언어는 새 세션에도 이어진다 - 예) 포털 로그인 화면에서 日本語를 고르고 탭을 잘못 골라 거절돼도 일본어 화면으로 돌아간다.
	 */
	private static void clearAuthentication(HttpServletRequest request) {
		SecurityContextHolder.clearContext();
		PreferredLocaleResolver.invalidateSessionKeepingLocale(request);
	}

	private static String expectedPortalRole(String portalRole) {
		if (portalRole == null || portalRole.isBlank()) {
			return null;
		}
		return switch (portalRole.trim().toUpperCase()) {
			case "ARTIST" -> "ROLE_ARTIST";
			case "AGENCY" -> "ROLE_AGENCY";
			default -> null;
		};
	}
}
