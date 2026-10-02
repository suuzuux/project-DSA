package megane6.weplanet.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.i18n.Messages;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Map;

// FIX-02: CSRF 토큰이 없거나 틀려서 요청이 막혔을 때의 응답.
// 이 거절은 컨트롤러에 닿기 전(필터 단계)에 일어나서 GlobalExceptionHandler 가 잡지 못한다. 그래서 여기서 직접 처리한다.
// 정상 사용자에게 생기는 경우는 대부분 "탭을 오래 열어 둔 사이 로그인/로그아웃해서 화면 속 토큰이 낡은 것"이라
// 새로고침하면 해결된다는 안내를 보여준다.
// - fetch 요청 : 화면 이동 없이 JSON {"success":false,"message":"..."} (GlobalExceptionHandler 와 같은 모양)
// - 폼 제출   : 안내 화면(/csrf-expired)으로 이동
// CSRF 가 아닌 403(권한 없음)은 스프링 기본 처리 그대로 둔다.
@Slf4j
@Component
@RequiredArgsConstructor
public class CsrfAccessDeniedHandler implements AccessDeniedHandler {

	private final Messages messages;
	private final JsonMapper jsonMapper; // 스프링 부트가 만들어 둔 JSON 변환기
	private final AccessDeniedHandler defaultHandler = new AccessDeniedHandlerImpl();

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
					   AccessDeniedException accessDeniedException) throws IOException, ServletException {
		if (!(accessDeniedException instanceof CsrfException)) {
			defaultHandler.handle(request, response, accessDeniedException);
			return;
		}

		log.warn("CSRF 토큰 검증 실패: {} {} ({})", request.getMethod(), request.getRequestURI(),
				accessDeniedException.getClass().getSimpleName());

		if (isAsync(request)) {
			response.setStatus(HttpStatus.FORBIDDEN.value());
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			response.setCharacterEncoding("UTF-8");
			jsonMapper.writeValue(response.getWriter(),
					Map.of("success", false, "message", messages.get("error.csrf")));
			return;
		}
		response.sendRedirect(request.getContextPath() + "/csrf-expired");
	}

	// fetch 로 온 요청인지 - 우리 JS 는 X-Requested-With 를 붙이거나(GlobalExceptionHandler 와 같은 기준),
	// csrf.js 가 X-XSRF-TOKEN 헤더를 붙이거나, JSON 을 주고받는다. 일반 폼 제출에는 셋 다 없다.
	private boolean isAsync(HttpServletRequest request) {
		if (request.getHeader("X-Requested-With") != null || request.getHeader("X-XSRF-TOKEN") != null) {
			return true;
		}
		String accept = request.getHeader("Accept");
		String contentType = request.getContentType();
		return (accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE))
				|| (contentType != null && contentType.contains(MediaType.APPLICATION_JSON_VALUE));
	}
}
