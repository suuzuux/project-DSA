package megane6.weplanet.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

import java.util.Map;

/**
 * 서비스 로직에서 던지는 IllegalArgumentException / IllegalStateException을
 * 스프링 기본 Whitelabel 에러 페이지(스택 트레이스 그대로 노출) 대신
 * 간단한 안내 화면으로 바꿔서 보여줌.
 * <p>
 * fetch(비동기)로 온 요청이면 화면 이동 없이 JSON({"success":false,"message":"..."})으로 돌려줘서,
 * 신고 중복 같은 경우도 페이지 전체 새로고침 없이 그 자리에서 실패 메시지를 보여줄 수 있게 함.
 * <p>
 * @ControllerAdvice : "모든 컨트롤러를 감시하고 있다가, 어디서든 예외가 터지면 이 클래스가 대신 처리한다"는 표시.
 * 즉 PostController 안에서 try-catch를 일일이 안 써도, 여기 한 곳에서 예외 처리를 몰아서 담당함.
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    private boolean isAsync(HttpServletRequest request) {
        return "fetch".equals(request.getHeader("X-Requested-With"));
    }

    // 잘못된 요청(존재하지 않는 게시글/유저 id 등)
    @ExceptionHandler(IllegalArgumentException.class)
    public Object handleIllegalArgument(IllegalArgumentException e, HttpServletRequest request) {
        log.warn("잘못된 요청: {}", e.getMessage());

        if (isAsync(request)) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }

        ModelAndView mav = new ModelAndView("errorMessage");
        mav.addObject("message", e.getMessage());
        mav.setStatus(HttpStatus.BAD_REQUEST);
        return mav;
    }

    // 권한/상태 위반(본인 글이 아님, 이미 신고함, 관리자 아님 등)
    @ExceptionHandler(IllegalStateException.class)
    public Object handleIllegalState(IllegalStateException e, HttpServletRequest request) {
        log.warn("허용되지 않은 요청: {}", e.getMessage());

        if (isAsync(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", e.getMessage()));
        }

        ModelAndView mav = new ModelAndView("errorMessage");
        mav.addObject("message", e.getMessage());
        mav.setStatus(HttpStatus.FORBIDDEN);
        return mav;
    }
}
