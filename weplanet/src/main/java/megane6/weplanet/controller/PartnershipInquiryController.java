package megane6.weplanet.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.service.email.PartnershipInquiryService;
import megane6.weplanet.service.email.PartnershipInquiryService.PartnershipInquiry;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

// [입점 신청] 메인 페이지 푸터의 "입점 신청" 링크로 들어오는 화면.
// 로그인 없이 접근할 수 있어야 해서 SecurityConfig의 PUBLIC_URLS에 /partnership 을 넣어뒀다.
@Slf4j
@Controller
@RequiredArgsConstructor
public class PartnershipInquiryController {

    // 로그인 없이 메일을 보낼 수 있는 폼이라 새로고침 연타나 장난성 반복 제출로
    // 운영자 메일함이 쉽게 더러워질 수 있음. 세션 기준으로 최소 간격만 막아둔다.
    private static final String SESSION_KEY_LAST_SENT = "PARTNERSHIP_LAST_SENT_AT";
    private static final long COOLDOWN_SECONDS = 60;

    private final PartnershipInquiryService partnershipInquiryService;

    @GetMapping("/partnership")
    public String form() {
        return "partnership";
    }

    @PostMapping("/partnership")
    public String submit(
            @RequestParam(required = false) String companyName,
            @RequestParam(required = false) String managerName,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String message,
            HttpSession session,
            Model model) {

        // 입력값을 그대로 돌려줘서, 오류가 나도 사용자가 처음부터 다시 쓰지 않아도 되게 함
        model.addAttribute("form", formValues(companyName, managerName, email, phone, category, message));

        if (isBlank(companyName) || isBlank(managerName) || isBlank(email) || isBlank(message)) {
            model.addAttribute("errorMessage", "업체명, 담당자명, 이메일, 문의 내용은 필수 입력입니다.");
            return "partnership";
        }
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            model.addAttribute("errorMessage", "이메일 주소 형식을 확인해주세요.");
            return "partnership";
        }
        if (isOnCooldown(session)) {
            model.addAttribute("errorMessage", "방금 신청이 접수되었습니다. 잠시 후 다시 시도해주세요.");
            return "partnership";
        }

        try {
            partnershipInquiryService.send(new PartnershipInquiry(
                    companyName.trim(), managerName.trim(), email.trim(),
                    trimOrNull(phone), trimOrNull(category), message.trim()));
        } catch (Exception e) {
            // 메일 서버 문제로 실패했을 때 흰 화면이 뜨지 않도록 폼에 남겨두고 안내한다.
            log.warn("입점 신청 메일 발송 실패: company={}", companyName, e);
            model.addAttribute("errorMessage", "지금은 신청을 접수할 수 없습니다. 잠시 후 다시 시도해주세요.");
            return "partnership";
        }

        session.setAttribute(SESSION_KEY_LAST_SENT, LocalDateTime.now());
        // 새로고침으로 같은 메일이 다시 발송되지 않도록 완료 화면은 리다이렉트로 넘긴다(PRG 패턴).
        return "redirect:/partnership?submitted";
    }

    private boolean isOnCooldown(HttpSession session) {
        Object lastSent = session.getAttribute(SESSION_KEY_LAST_SENT);
        if (!(lastSent instanceof LocalDateTime sentAt)) {
            return false;
        }
        return Duration.between(sentAt, LocalDateTime.now()).getSeconds() < COOLDOWN_SECONDS;
    }

    private Map<String, String> formValues(String companyName, String managerName, String email,
                                           String phone, String category, String message) {
        Map<String, String> values = new HashMap<>();
        values.put("companyName", nullToEmpty(companyName));
        values.put("managerName", nullToEmpty(managerName));
        values.put("email", nullToEmpty(email));
        values.put("phone", nullToEmpty(phone));
        values.put("category", nullToEmpty(category));
        values.put("message", nullToEmpty(message));
        return values;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String trimOrNull(String value) {
        return isBlank(value) ? null : value.trim();
    }
}
