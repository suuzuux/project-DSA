package megane6.weplanet.controller;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import megane6.weplanet.i18n.Messages;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

// FIX-02: 폼 제출이 CSRF 토큰 검증에 실패했을 때 CsrfAccessDeniedHandler 가 보내는 안내 화면.
// 필터 단계에서는 Thymeleaf 화면을 그릴 수 없어서, 공통 에러 화면(errorMessage.html)을 여기서 대신 띄운다.
@Controller
@RequiredArgsConstructor
public class CsrfErrorController {

    private final Messages messages;

    @GetMapping("/csrf-expired")
    public String csrfExpired(Model model, HttpServletResponse response) {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        model.addAttribute("message", messages.get("error.csrf"));
        model.addAttribute("status", HttpStatus.FORBIDDEN.value());
        return "errorMessage";
    }
}
