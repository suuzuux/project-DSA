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

/**
 * 스프링 기본 Whitelabel 에러 페이지(스택 트레이스 그대로 노출)가 사용자에게 보이지 않도록,
 * 컨트롤러에서 터지는 모든 예외를 여기서 받아 안내 화면 또는 JSON으로 바꿔서 돌려줌.
 * <p>
 * fetch(비동기)로 온 요청이면 화면 이동 없이 JSON({"success":false,"message":"..."})으로 돌려줘서,
 * 관리자 아님/이미 신고함 같은 경우도 페이지 전체 새로고침 없이 그 자리에서 실패 메시지를 보여줄 수 있게 함.
 * <p>
 * @ControllerAdvice : "모든 컨트롤러를 감시하고 있다가, 어디서든 예외가 터지면 이 클래스가 대신 처리한다"는 표시.
 * 즉 ChatController, PostController 안에서 try-catch를 일일이 안 써도, 여기 한 곳에서 예외 처리를 몰아서 담당함.
 */
@Slf4j
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    // SETTINGS-03 커밋3: 예외 메시지는 메시지 키로 던지고(예: "error.post.contentRequired"), 여기서 사용자의
    // 로케일 문구로 바꿔서 내보낸다. 아직 키로 바꾸지 않은 한국어 문구는 그대로 통과한다(Messages.resolve 참고).
    private final Messages messages;

    private boolean isAsync(HttpServletRequest request) {
        return "fetch".equals(request.getHeader("X-Requested-With"));
    }

    /**
     * 화면(HTML) 응답과 JSON 응답을 한 곳에서 만들어주는 공통 처리.
     * 어떤 예외든 결국 이 메서드를 거치므로, Whitelabel 페이지로 새어나가지 않음.
     */
    private Object respond(HttpServletRequest request, HttpStatus status, String messageOrKey) {
        return respondMessage(request, status, messages.resolve(messageOrKey));
    }

    // 예외를 그대로 받는 버전 - 값을 들고 다니는 예외(LocalizedMessage)의 {0} 자리까지 채워서 번역한다.
    // 단, 예외 메시지를 화면에 쓰는 건 우리 코드가 사용자에게 보여주려고 던진 예외일 때만이다.
    // Tomcat·Spring·JDK 같은 라이브러리가 던진 예외의 메시지는 내부 구현 정보라서 상태별 일반 문구로 바꾼다
    // (예: 글자가 깨진 요청에 Tomcat 의 "Character decoding failed. Parameter [...]" 문구가 그대로 보였다).
    private Object respond(HttpServletRequest request, HttpStatus status, Throwable e) {
        if (!isThrownByOurCode(e)) {
            return respond(request, status, genericMessageKey(status));
        }
        return respondMessage(request, status, messages.resolve(e));
    }

    // 메시지 키/값을 들고 다니는 예외이거나, 예외가 만들어진 곳(스택 맨 위)이 우리 패키지면 우리 코드가 던진 것
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

        ModelAndView mav = new ModelAndView("errorMessage");
        mav.addObject("message", message);
        mav.addObject("status", status.value());
        mav.setStatus(status);
        return mav;
    }

    // 로그인 안 하고 글쓰기/댓글/좋아요 등을 시도했을 때
    @ExceptionHandler(AuthenticationRequiredException.class)
    public Object handleAuthenticationRequired(AuthenticationRequiredException e, HttpServletRequest request) {
        log.warn("로그인 필요한 요청을 비로그인 상태로 시도함: {}", request.getRequestURI());

        if (isAsync(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", messages.resolve(e)));
        }
        return "redirect:/login";
    }

    // 로그인 세션엔 "이 유저로 로그인됨"이라고 남아있는데, 그 유저가 DB에는 더 이상 없는 경우
    // (관리자가 계정을 직접 삭제한 경우 등). 그냥 IllegalArgumentException처럼 에러 화면만 보여주면,
    // 세션이 여전히 그 죽은 principal을 물고 있어서 "홈으로" 버튼을 눌러도 "/"에서 똑같은 예외가 또 터진다 -
    // 그래서 여기서 세션 자체를 정리(로그아웃)한 뒤 로그인 화면으로 보내서 무한 반복을 끊는다.
    @ExceptionHandler(StaleSessionException.class)
    public Object handleStaleSession(StaleSessionException e, HttpServletRequest request) {
        log.warn("세션의 로그인 유저가 더 이상 존재하지 않아 세션을 정리함: {}", e.getMessage());
        SecurityContextHolder.clearContext();
        // 세션을 버리되 화면 언어는 새 세션에 이어 붙인다 (안내 문구·로그인 화면이 한국어로 돌아가지 않게)
        PreferredLocaleResolver.invalidateSessionKeepingLocale(request);

        // AUTH-11: 정지·탈퇴 등으로 계정을 쓸 수 없게 된 경우는 "세션 만료" 대신 이용할 수 없는 계정이라고 안내한다
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

    // 요청 파라미터를 읽을 수 없는 경우 (글자 인코딩이 깨진 값 등). Tomcat 11 은 이때 IllegalStateException 의
    // 하위 예외를 던져서, 아래 "권한/상태 위반" 처리로 들어가 403 과 Tomcat 내부 문구가 그대로 나갔다.
    // 요청 형식 오류(400)로 따로 처리한다 (더 구체적인 예외 처리기가 우선 적용된다).
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

    /**
     * 위에서 못 잡은 나머지 전부 (NPE, DB 오류 등) - 최후의 그물.
     * 이게 있어야 Whitelabel 페이지가 사용자에게 노출되지 않음.
     * 내부 오류 메시지는 그대로 보여주면 정보가 새므로, 로그에만 남기고 화면엔 일반 문구를 띄움.
     */
    @ExceptionHandler(Exception.class)
    public Object handleUnexpected(Exception e, HttpServletRequest request) {
        log.error("예상치 못한 오류: {} {}", request.getMethod(), request.getRequestURI(), e);
        return respond(request, HttpStatus.INTERNAL_SERVER_ERROR, "error.unexpected");
    }
}
