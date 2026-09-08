package megane6.weplanet.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.UserStatus;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.security.SocialLoginSessionSupport;
import megane6.weplanet.service.email.SignupEmailVerificationService;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Controller
@RequestMapping("/login/reactivate")
@RequiredArgsConstructor
public class DormantAccountReactivationController {

    // 소셜 로그인 콜백(OAuth2LoginSuccessHandler)에서 "이 사람이 재활성화 대상"이라는 걸
    // 세션에 남겨두는 키. 클라이언트가 임의로 조작 못 하게, username을 URL/폼으로 안 받고
    // 서버가 이미 알고 있는 이 값만으로 대상을 특정한다.
    public static final String SESSION_KEY_PENDING_REACTIVATION_USER_ID = "PENDING_DORMANT_REACTIVATION_USER_ID";

    private final UserRepository userRepository;
    private final SignupEmailVerificationService emailVerificationService;
    private final SocialLoginSessionSupport socialLoginSessionSupport;

    @GetMapping
    public String form(HttpSession session, Model model) {
        model.addAttribute("socialFlow", pendingUserId(session) != null);
        return "login/reactivate";
    }

    @PostMapping("/code")
    @ResponseBody
    public Map<String, Object> sendCode(@RequestParam(required = false) String username, HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        Optional<User> target = resolveTarget(username, session);
        if (target.isEmpty()) {
            result.put("success", false);
            result.put("message", "휴면 상태인 계정을 찾을 수 없습니다.");
            return result;
        }
        try {
            emailVerificationService.sendVerificationCode(target.get().getEmail());
            result.put("success", true);
            result.put("message", "가입 시 등록된 이메일로 인증코드를 보냈습니다.");
        } catch (Exception e) {
            log.error("[휴면계정 해제] 인증코드 발송 실패", e);
            result.put("success", false);
            result.put("message", "이메일 발송에 실패했습니다. 잠시 후 다시 시도해주세요.");
        }
        return result;
    }

    @PostMapping("/verify")
    @Transactional
    public String verifyAndReactivate(@RequestParam(required = false) String username,
                                       @RequestParam String code,
                                       HttpSession session,
                                       HttpServletRequest request,
                                       HttpServletResponse response,
                                       Model model) {
        Optional<User> target = resolveTarget(username, session);
        if (target.isEmpty()) {
            model.addAttribute("errorMessage", "휴면 상태인 계정을 찾을 수 없습니다.");
            return "login/reactivate";
        }
        User user = target.get();
        if (!emailVerificationService.verifyCode(user.getEmail(), code)) {
            model.addAttribute("errorMessage", "인증코드가 일치하지 않거나 만료되었습니다.");
            return "login/reactivate";
        }
        emailVerificationService.clear(user.getEmail());
        user.reactivate();
        if (session != null) {
            session.removeAttribute(SESSION_KEY_PENDING_REACTIVATION_USER_ID);
        }
        socialLoginSessionSupport.loginAs(user, request, response);
        return "redirect:/";
    }

    private Optional<User> resolveTarget(String username, HttpSession session) {
        Long pendingUserId = pendingUserId(session);
        if (pendingUserId != null) {
            return userRepository.findById(pendingUserId).filter(u -> u.getStatus() == UserStatus.DORMANT);
        }
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByUsername(username).filter(u -> u.getStatus() == UserStatus.DORMANT);
    }

    private Long pendingUserId(HttpSession session) {
        return session != null ? (Long) session.getAttribute(SESSION_KEY_PENDING_REACTIVATION_USER_ID) : null;
    }
}
