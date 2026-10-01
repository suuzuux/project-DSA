package megane6.weplanet.i18n;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import megane6.weplanet.domain.entity.enumfolder.Language;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.LocaleResolver;

import java.util.Locale;

@Component("localeResolver")
public class PreferredLocaleResolver implements LocaleResolver {

    private static final String SESSION_ATTR = "weplanet.locale";
    // 로그인 전(비로그인 상태)에 포털 로그인 화면 등의 언어 메뉴에서 직접 언어를 골랐다는 표시.
    // 아티스트/에이전시는 로그인 화면에서 고른 언어를 로그인 후에도 유지하고 계정 선호 언어로 저장한다(LoginSuccessHandler).
    private static final String EXPLICIT_CHOICE_ATTR = "weplanet.locale.explicit";

    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        // 관리자 화면은 한국어 고정: /admin 아래 화면(로그인 화면 포함)과 관리자 계정의 모든 요청은 항상 한국어
        if (isKoreanOnlyAdminRequest(request)) {
            return Locale.KOREAN;
        }
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object stored = session.getAttribute(SESSION_ATTR);
            if (stored instanceof Locale locale) {
                return locale;
            }
        }
        return Locale.KOREAN;
    }

    @Override
    public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
        request.getSession().setAttribute(SESSION_ATTR, locale != null ? locale : Locale.KOREAN);
    }

    private static boolean isKoreanOnlyAdminRequest(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.equals("/admin") || path.startsWith("/admin/") || path.startsWith("/chat/admin")) {
            return true;
        }
        return isAdmin(SecurityContextHolder.getContext().getAuthentication());
    }

    public static boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    /** 세션에 저장된 화면 언어. 관리자 한국어 고정을 적용하기 전의 원래 값이며, 저장된 게 없으면 null */
    public static Locale storedLocale(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(SESSION_ATTR) instanceof Locale locale) {
            return locale;
        }
        return null;
    }

    /** storedLocale 로 읽어 둔 언어를 (새) 세션에 다시 넣는다. null 이면 아무것도 하지 않는다 */
    public static void restoreLocale(HttpServletRequest request, Locale locale) {
        if (locale != null) {
            request.getSession(true).setAttribute(SESSION_ATTR, locale);
        }
    }

    /**
     * 세션을 버리고 새 세션을 연다(로그인 거절·회원탈퇴·연동 해제·세션 정리 등).
     * 화면 언어는 세션에만 저장되므로 그냥 버리면 다음 화면이 한국어로 돌아간다 - 그래서 버리기 전에
     * 언어와 "로그인 전에 직접 고른 언어" 표시를 읽어 두었다가 새 세션에 다시 넣는다.
     * 새 세션은 항상 연다: 버리기만 하면 브라우저의 이전 세션 쿠키 때문에 invalidSessionUrl(/login?expired)로 튕긴다.
     */
    public static void invalidateSessionKeepingLocale(HttpServletRequest request) {
        Locale kept = storedLocale(request);
        boolean explicit = hasExplicitChoice(request);
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        request.getSession(true);
        restoreLocale(request, kept);
        if (explicit) {
            markExplicitChoice(request);
        }
    }

    public static void markExplicitChoice(HttpServletRequest request) {
        request.getSession().setAttribute(EXPLICIT_CHOICE_ATTR, Boolean.TRUE);
    }

    public static boolean hasExplicitChoice(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null && Boolean.TRUE.equals(session.getAttribute(EXPLICIT_CHOICE_ATTR));
    }

    public static void clearExplicitChoice(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(EXPLICIT_CHOICE_ATTR);
        }
    }

    // toLocale 의 반대 방향. 신청서처럼 "지금 화면 언어"를 DB에 저장해 둘 때 쓴다 (모르는 언어는 KO)
    public static Language toLanguage(Locale locale) {
        if (locale == null) {
            return Language.KO;
        }
        return switch (locale.getLanguage()) {
            case "ja" -> Language.JA;
            case "en" -> Language.EN;
            default -> Language.KO;
        };
    }

    public static Locale toLocale(Language language) {
        if (language == null) {
            return Locale.KOREAN;
        }
        return switch (language) {
            case KO -> Locale.KOREAN;
            case JA -> Locale.JAPANESE;
            case EN -> Locale.ENGLISH;
        };
    }
}
