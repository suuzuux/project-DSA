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
    // 로그인 전에 언어를 직접 골랐다는 표시 (포털 로그인 후에도 유지).
    private static final String EXPLICIT_CHOICE_ATTR = "weplanet.locale.explicit";

    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        // 관리자 화면과 관리자 계정 요청은 항상 한국어
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

    /** 세션에 저장된 화면 언어 (없으면 null) */
    public static Locale storedLocale(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(SESSION_ATTR) instanceof Locale locale) {
            return locale;
        }
        return null;
    }

    /** 저장해 둔 언어를 새 세션에 다시 넣는다. */
    public static void restoreLocale(HttpServletRequest request, Locale locale) {
        if (locale != null) {
            request.getSession(true).setAttribute(SESSION_ATTR, locale);
        }
    }

    /** 세션을 새로 열면서 화면 언어를 이어 붙인다 (/login?expired 방지). */
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

    // Locale → Language (모르는 언어는 KO)
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
