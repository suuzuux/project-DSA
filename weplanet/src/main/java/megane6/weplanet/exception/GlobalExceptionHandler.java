package megane6.weplanet.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 서비스 로직에서 던지는 IllegalArgumentException / IllegalStateException을
 * 스프링 기본 Whitelabel 에러 페이지(스택 트레이스 그대로 노출) 대신
 * 간단한 안내 화면으로 바꿔서 보여줌.
 * <p>
 * 지금까지 이 클래스가 비어있어서, "권한 없음", "이미 신고함" 같은
 * 정상적인 예외 상황도 전부 서버 내부 스택 트레이스가 그대로 사용자에게 노출되고 있었음.
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    // 잘못된 요청(존재하지 않는 게시글/유저 id 등)
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleIllegalArgument(IllegalArgumentException e, Model model) {
        log.warn("잘못된 요청: {}", e.getMessage());
        model.addAttribute("message", e.getMessage());
        return "errorMessage";
    }

    // 권한/상태 위반(본인 글이 아님, 이미 신고함, 관리자 아님 등)
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleIllegalState(IllegalStateException e, Model model) {
        log.warn("허용되지 않은 요청: {}", e.getMessage());
        model.addAttribute("message", e.getMessage());
        return "errorMessage";
    }
}
