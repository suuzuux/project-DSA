package megane6.weplanet.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.i18n.Messages;
import megane6.weplanet.i18n.PreferredLocaleResolver;
import org.apache.tomcat.util.http.InvalidParameterException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

/** 모든 컨트롤러 예외를 안내 화면 또는 JSON(fetch 요청)으로 바꿔 돌려준다. */
@Slf4j
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    // 예외 메시지 키를 사용자 로케일 문구로 바꾼다.
    private final Messages messages;

    private boolean isAsync(HttpServletRequest request) {
        return "fetch".equals(request.getHeader("X-Requested-With"));
    }

    /** HTML 응답과 JSON 응답 공통 처리 */
    private Object respond(HttpServletRequest request, HttpStatus status, String messageOrKey) {
        return respondMessage(request, status, messages.resolve(messageOrKey));
    }

    // 우리 코드 예외만 메시지를 보여주고, 라이브러리 예외는 상태별 일반 문구로 바꾼다.
    private Object respond(HttpServletRequest request, HttpStatus status, Throwable e) {
        if (!isThrownByOurCode(e)) {
            return respond(request, status, genericMessageKey(status));
        }
        return respondMessage(request, status, messages.resolve(e));
    }

    // 우리 코드가 던진 예외인지 (메시지 키 예외이거나 스택 맨 위가 우리 패키지).
    private static boolean isThrownByOurCode(Throwable e) {
        if (e instanceof LocalizedMessage) {
            return true;
        }
        StackTraceElement[] trace = e.getStackTrace();
        return trace.length > 0 && trace[0].getClassName().startsWith("megane6.weplanet.");
    }

    private static String genericMessageKey(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "error.badRequest";
            case FORBIDDEN -> "error.forbidden";
            default -> "error.unexpected";
        };
    }

    private Object respondMessage(HttpServletRequest request, HttpStatus status, String message) {
        if (isAsync(request)) {
            return ResponseEntity.status(status).body(Map.of("success", false, "message", message));
        }

        ModelAndView mav = new ModelAndView("common/error/errorMessage");
        mav.addObject("message", message);
        mav.addObject("status", status.value());
        mav.setStatus(status);
        return mav;
    }

    // 비로그인으로 로그인 필요 동작을 시도했을 때
    @ExceptionHandler(AuthenticationRequiredException.class)
    public Object handleAuthenticationRequired(AuthenticationRequiredException e, HttpServletRequest request) {
        log.warn("로그인 필요한 요청을 비로그인 상태로 시도함: {}", request.getRequestURI());

        if (isAsync(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", messages.resolve(e)));
        }
        return "redirect:/login";
    }

    // 세션의 유저가 DB 에 없으면 세션을 정리하고 로그인 화면으로 보낸다.
    @ExceptionHandler(StaleSessionException.class)
    public Object handleStaleSession(StaleSessionException e, HttpServletRequest request) {
        log.warn("세션의 로그인 유저가 더 이상 존재하지 않아 세션을 정리함: {}", e.getMessage());
        SecurityContextHolder.clearContext();
        // 세션을 버리되 화면 언어는 새 세션에 유지한다.
        PreferredLocaleResolver.invalidateSessionKeepingLocale(request);

        // 정지·탈퇴 계정은 이용할 수 없는 계정이라고 안내한다.
        boolean inactive = e instanceof InactiveAccountSessionException;
        if (isAsync(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", inactive
                            ? messages.get("error.accountUnavailable")
                            : messages.get("error.sessionExpired")));
        }
        return inactive ? "redirect:/login?accountUnavailable=true" : "redirect:/login?sessionExpired=true";
    }

    // 잘못된 요청(존재하지 않는 게시글/유저 id 등)
    @ExceptionHandler(IllegalArgumentException.class)
    public Object handleIllegalArgument(IllegalArgumentException e, HttpServletRequest request) {
        log.warn("잘못된 요청: {}", e.getMessage());
        return respond(request, HttpStatus.BAD_REQUEST, e);
    }

    // 파라미터를 읽을 수 없는 경우는 400 으로 따로 처리한다 (Tomcat 11).
    @ExceptionHandler(InvalidParameterException.class)
    public Object handleInvalidParameter(InvalidParameterException e, HttpServletRequest request) {
        log.warn("요청 파라미터를 읽을 수 없음: {} - {}", request.getRequestURI(), e.getMessage());
        return respond(request, HttpStatus.BAD_REQUEST, "error.badRequest");
    }

    // 권한/상태 위반(본인 글이 아님, 이미 신고함, 관리자 아님 등)
    @ExceptionHandler(IllegalStateException.class)
    public Object handleIllegalState(IllegalStateException e, HttpServletRequest request) {
        log.warn("허용되지 않은 요청: {}", e.getMessage());
        return respond(request, HttpStatus.FORBIDDEN, e);
    }
    
    // 권한 없는 사용자가 주소로 접근 (403)
    @ExceptionHandler(AccessDeniedException.class)
    public Object handleAccessDenied(
            AccessDeniedException e,
            HttpServletRequest request
    ) {
        log.warn("접근 권한이 없는 요청: {} - {}", request.getRequestURI(), e.getMessage());
        return respond(request, HttpStatus.FORBIDDEN, e);
    }

    // 없는 주소로 접근 (404) - 정적 리소스 포함
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public Object handleNotFound(Exception e, HttpServletRequest request) {
        log.warn("존재하지 않는 주소: {}", request.getRequestURI());
        return respond(request, HttpStatus.NOT_FOUND, "error.pageNotFound");
    }

    // 필수 파라미터 누락 / 타입 불일치 / 잘못된 JSON 본문
    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentNotValidException.class
    })
    public Object handleBadRequest(Exception e, HttpServletRequest request) {
        log.warn("요청 형식 오류: {} - {}", request.getRequestURI(), e.getMessage());
        return respond(request, HttpStatus.BAD_REQUEST, "error.badRequest");
    }

    // GET으로 열어야 할 주소를 POST로 부르는 등
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Object handleMethodNotAllowed(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        log.warn("지원하지 않는 요청 방식: {} {}", request.getMethod(), request.getRequestURI());
        return respond(request, HttpStatus.METHOD_NOT_ALLOWED, "error.methodNotAllowed");
    }

    // 첨부파일 용량 초과
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Object handleUploadTooLarge(MaxUploadSizeExceededException e, HttpServletRequest request) {
        log.warn("업로드 용량 초과: {}", request.getRequestURI());
        return respond(request, HttpStatus.CONTENT_TOO_LARGE, "error.uploadTooLarge");
    }

    /** 나머지 모든 예외 - 내부 메시지는 로그에만 남기고 일반 문구를 보여준다. */
    @ExceptionHandler(Exception.class)
    public Object handleUnexpected(Exception e, HttpServletRequest request) {
        log.error("예상치 못한 오류: {} {}", request.getMethod(), request.getRequestURI(), e);
        return respond(request, HttpStatus.INTERNAL_SERVER_ERROR, "error.unexpected");
    }
}
