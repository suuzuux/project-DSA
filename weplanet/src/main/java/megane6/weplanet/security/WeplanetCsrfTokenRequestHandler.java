package megane6.weplanet.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

import java.util.function.Supplier;

// FIX-02: 요청에 실려 온 CSRF 토큰을 꺼내는 방법을 정한다. 스프링의 csrf.spa() 설정을 바탕으로 한 가지를 더했다.
//
// 토큰은 두 가지 모양으로 들어온다.
//  1) 서버가 그린 화면 속 토큰 - Thymeleaf 폼의 숨은 _csrf 필드. 매번 다른 값으로 "섞어서(XOR)" 내려준다
//     (같은 값이 HTML 에 반복해서 찍히면 압축 길이를 이용한 BREACH 공격으로 알아낼 수 있어서).
//  2) 쿠키 원본 토큰 - csrf.js 가 XSRF-TOKEN 쿠키를 읽어 헤더나 폼 필드에 넣는 값. 섞이지 않은 그대로다.
//
// spa() 는 "헤더면 원본, 폼 필드면 섞인 값"만 받는다. 그런데 shell.js 처럼 JS 가 직접 만든 폼은
// 서버가 토큰을 그려 줄 수 없어서 csrf.js 가 원본 토큰을 폼 필드에 넣는다. 그래서 폼 필드는
// 섞인 값으로 먼저 풀어 보고, 안 풀리면 원본으로 한 번 더 확인한다. (원본도 쿠키 값과 정확히 같아야 통과)
public class WeplanetCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

	private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
	private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
		// Thymeleaf 화면에는 항상 섞인 값을 그리도록 (BREACH 대비)
		xor.handle(request, response, csrfToken);
	}

	@Override
	public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
		if (StringUtils.hasText(request.getHeader(csrfToken.getHeaderName()))) {
			return plain.resolveCsrfTokenValue(request, csrfToken);
		}
		String unmasked = xor.resolveCsrfTokenValue(request, csrfToken);
		return unmasked != null ? unmasked : plain.resolveCsrfTokenValue(request, csrfToken);
	}
}
