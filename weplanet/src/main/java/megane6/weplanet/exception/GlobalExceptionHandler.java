package megane6.weplanet.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.dto.ActionResultResponse;
import megane6.weplanet.support.FetchRequests;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public Object handleIllegalArgument(IllegalArgumentException e, HttpServletRequest request) {
        log.warn("잘못된 요청: {}", e.getMessage());
        return errorResponse(e.getMessage(), HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(IllegalStateException.class)
    public Object handleIllegalState(IllegalStateException e, HttpServletRequest request) {
        log.warn("허용되지 않은 요청: {}", e.getMessage());
        return errorResponse(e.getMessage(), HttpStatus.FORBIDDEN, request);
    }

    private Object errorResponse(String message, HttpStatus status, HttpServletRequest request) {
        if (FetchRequests.isFetch(request)) {
            return ResponseEntity.status(status).body(new ActionResultResponse(false, message));
        }
        ModelAndView mav = new ModelAndView("errorMessage");
        mav.addObject("message", message);
        mav.setStatus(status);
        return mav;
    }
}
