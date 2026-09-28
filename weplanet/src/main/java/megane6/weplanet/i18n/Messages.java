package megane6.weplanet.i18n;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * SETTINGS-03 커밋3: 컨트롤러/서비스 여러 곳에서 공통으로 쓰는 메시지 해석 도우미.
 * <p>
 * 커밋1~4에서는 클래스마다 msg(code) 헬퍼를 두고 MessageSource를 직접 주입했는데, 커밋3에서
 * "예외 메시지를 메시지 키로 던지고 화면에 내보내는 쪽에서 번역한다"는 규칙을 도입하면서
 * GlobalExceptionHandler와 e.getMessage()를 flash/JSON으로 내보내는 여러 컨트롤러가 같은 해석 로직을
 * 써야 해서 한 곳으로 모았다.
 * <p>
 * resolve()는 키일 수도 있고 이미 완성된 문장일 수도 있는 값을 받는다. 아직 키로 바꾸지 않은(커밋5 대상)
 * 한국어 예외 문구가 들어와도 MessageSource에 그런 키가 없으므로 원문이 그대로 돌아온다.
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
