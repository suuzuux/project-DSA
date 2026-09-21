package megane6.weplanet.i18n;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import megane6.weplanet.domain.entity.enumfolder.Language;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.LocaleResolver;

import java.util.Locale;

@Component("localeResolver")
public class PreferredLocaleResolver implements LocaleResolver {

    private static final String SESSION_ATTR = "weplanet.locale";

    @Override
    public Locale resolveLocale(HttpServletRequest request) {
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
