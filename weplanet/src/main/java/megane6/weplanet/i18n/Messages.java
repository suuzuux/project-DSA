package megane6.weplanet.i18n;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.exception.LocalizedMessage;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 메시지 키를 현재 화면 언어 문구로 바꾸는 공용 도우미 (예외 메시지 키 번역 등).
 * 키가 아닌 완성된 문장이 들어오면 그대로 돌려준다.
 */
@Component
@RequiredArgsConstructor
public class Messages {

	private final MessageSource messageSource;

	/** 현재 요청 로케일로 키를 해석한다. args가 없으면 MessageFormat을 돌리지 않는다({0}이 그대로 남음). */
	public String get(String code, Object... args) {
		return messageSource.getMessage(
				code,
				(args == null || args.length == 0) ? null : args,
				LocaleContextHolder.getLocale()
		);
	}

	/** 언어를 직접 정해서 키를 해석한다 (예: 총공 배너 제목 번역을 기다리는 동안은 한국어 문장 틀로). */
	public String get(Locale locale, String code, Object... args) {
		return messageSource.getMessage(
				code,
				(args == null || args.length == 0) ? null : args,
				locale
		);
	}

	/** 현재 요청 로케일로 키를 해석하고, 등록된 문구가 없으면 defaultText 를 돌려준다 (DB 기본값을 함께 쓰는 곳). */
	public String getOrDefault(String code, String defaultText) {
		return messageSource.getMessage(code, null, defaultText, LocaleContextHolder.getLocale());
	}

	/**
	 * 예외 메시지처럼 "메시지 키 또는 완성된 문장"인 값을 현재 로케일 문구로 바꾼다.
	 * 키가 아니면(=등록된 메시지가 없으면) 입력값을 그대로 돌려준다.
	 */
	public String resolve(String codeOrText) {
		if (codeOrText == null || codeOrText.isBlank()) {
			return codeOrText;
		}
		return resolve(codeOrText, LocaleContextHolder.getLocale());
	}

	/**
	 * 예외를 현재 화면 언어 문구로 바꾼다 - 값을 들고 다니는 예외(LocalizedMessage)는 키 + 값으로 번역한다.
	 * catch 블록에서는 resolve(e.getMessage()) 대신 이걸 써야 값이 빠지지 않는다.
	 */
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

	/**
	 * 요청 로케일이 없는 곳(STOMP @MessageMapping 등)에서 받는 사람의 로케일을 직접 지정해 해석한다.
	 * 호출부는 보통 PreferredLocaleResolver.toLocale(user.getPreferredLanguage())로 로케일을 만든다.
	 */
	public String resolve(String codeOrText, Locale locale) {
		if (codeOrText == null || codeOrText.isBlank()) {
			return codeOrText;
		}
		return messageSource.getMessage(codeOrText, null, codeOrText, locale != null ? locale : Locale.KOREAN);
	}
}
