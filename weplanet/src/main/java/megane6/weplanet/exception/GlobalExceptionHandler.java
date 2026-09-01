package megane6.weplanet.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
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
public class GlobalExceptionHandler {

    private boolean isAsync(HttpServletRequest request) {
        return "fetch".equals(request.getHeader("X-Requested-With"));
    }

    /**
     * 화면(HTML) 응답과 JSON 응답을 한 곳에서 만들어주는 공통 처리.
     * 어떤 예외든 결국 이 메서드를 거치므로, Whitelabel 페이지로 새어나가지 않음.
     */
    private Object respond(HttpServletRequest request, HttpStatus status, String message) {
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
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
        return "redirect:/login";
    }

    // 잘못된 요청(존재하지 않는 게시글/유저 id 등)
    @ExceptionHandler(IllegalArgumentException.class)
    public Object handleIllegalArgument(IllegalArgumentException e, HttpServletRequest request) {
        log.warn("잘못된 요청: {}", e.getMessage());
        return respond(request, HttpStatus.BAD_REQUEST, e.getMessage());
    }

    // 권한/상태 위반(본인 글이 아님, 이미 신고함, 관리자 아님 등)
    @ExceptionHandler(IllegalStateException.class)
    public Object handleIllegalState(IllegalStateException e, HttpServletRequest request) {
        log.warn("허용되지 않은 요청: {}", e.getMessage());
        return respond(request, HttpStatus.FORBIDDEN, e.getMessage());
    }
    
    // 권한 없는 사용자가 주소로 접근 (403)
    @ExceptionHandler(AccessDeniedException.class)
    public Object handleAccessDenied(
            AccessDeniedException e,
            HttpServletRequest request
    ) {
        log.warn("접근 권한이 없는 요청: {} - {}", request.getRequestURI(), e.getMessage());
        return respond(request, HttpStatus.FORBIDDEN, e.getMessage());
    }

    // 없는 주소로 접근 (404) - 정적 리소스 포함
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public Object handleNotFound(Exception e, HttpServletRequest request) {
        log.warn("존재하지 않는 주소: {}", request.getRequestURI());
        return respond(request, HttpStatus.NOT_FOUND, "요청하신 페이지를 찾을 수 없습니다.");
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
        return respond(request, HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다.");
    }

    // GET으로 열어야 할 주소를 POST로 부르는 등
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Object handleMethodNotAllowed(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        log.warn("지원하지 않는 요청 방식: {} {}", request.getMethod(), request.getRequestURI());
        return respond(request, HttpStatus.METHOD_NOT_ALLOWED, "잘못된 방식의 요청입니다.");
    }

    // 첨부파일 용량 초과
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Object handleUploadTooLarge(MaxUploadSizeExceededException e, HttpServletRequest request) {
        log.warn("업로드 용량 초과: {}", request.getRequestURI());
        return respond(request, HttpStatus.PAYLOAD_TOO_LARGE, "첨부파일 용량이 너무 큽니다.");
    }

    /**
     * 위에서 못 잡은 나머지 전부 (NPE, DB 오류 등) - 최후의 그물.
     * 이게 있어야 Whitelabel 페이지가 사용자에게 노출되지 않음.
     * 내부 오류 메시지는 그대로 보여주면 정보가 새므로, 로그에만 남기고 화면엔 일반 문구를 띄움.
     */
    @ExceptionHandler(Exception.class)
    public Object handleUnexpected(Exception e, HttpServletRequest request) {
        log.error("예상치 못한 오류: {} {}", request.getMethod(), request.getRequestURI(), e);
        return respond(request, HttpStatus.INTERNAL_SERVER_ERROR,
                "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
    }
}
