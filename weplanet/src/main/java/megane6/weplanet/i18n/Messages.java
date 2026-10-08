package megane6.weplanet.i18n;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.exception.LocalizedMessage;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.Locale;

/** 메시지 키를 현재 화면 언어 문구로 바꾸는 도우미 (완성된 문장은 그대로). */
@Component
@RequiredArgsConstructor
public class Messages {

	private final MessageSource messageSource;

	/** 현재 로케일로 키를 해석한다 (args 가 없으면 {0} 을 그대로 둠). */
	public String get(String code, Object... args) {
		return messageSource.getMessage(
				code,
				(args == null || args.length == 0) ? null : args,
				LocaleContextHolder.getLocale()
		);
	}

	/** 등록된 문구가 없으면 defaultText 를 돌려준다. */
	public String getOrDefault(String code, String defaultText) {
		return messageSource.getMessage(code, null, defaultText, LocaleContextHolder.getLocale());
	}

	/** 메시지 키 또는 완성된 문장을 현재 로케일 문구로 바꾼다. */
	public String resolve(String codeOrText) {
		if (codeOrText == null || codeOrText.isBlank()) {
			return codeOrText;
		}
		return resolve(codeOrText, LocaleContextHolder.getLocale());
	}

	/** 예외를 현재 로케일 문구로 바꾼다 (LocalizedMessage 는 값까지 채움). */
	public String resolve(Throwable e) {
		return resolve(e, LocaleContextHolder.getLocale());
	}

	public String resolve(Throwable e, Locale locale) {
		if (e == null) {
			return null;
		}
		if (e instanceof LocalizedMessage localized) {
			return messageSource.getMessage(
					localized.getMessageKey(),
					localized.getMessageArgs(),
					localized.getMessageKey(),
					locale != null ? locale : Locale.KOREAN
			);
		}
		return resolve(e.getMessage(), locale);
	}

	/** 요청 로케일이 없는 곳(STOMP 등)에서 지정한 로케일로 해석한다. */
	public String resolve(String codeOrText, Locale locale) {
		if (codeOrText == null || codeOrText.isBlank()) {
			return codeOrText;
		}
		return messageSource.getMessage(codeOrText, null, codeOrText, locale != null ? locale : Locale.KOREAN);
	}
}
