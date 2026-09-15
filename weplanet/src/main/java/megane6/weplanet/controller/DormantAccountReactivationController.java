package megane6.weplanet.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

// AUTH-10: 로컬 로그인 시도 중 휴면계정을 만난 경우와 소셜 로그인 시도 중 만난 경우를 화면(socialFlow)으로
// 구분하던 걸 없앴다. 이제 어느 쪽으로 들어와도 항상 같은 화면 - 아이디를 입력하면 가입 시 등록된 이메일로
// 인증코드를 보내주는 방식 - 하나로 통일한다. 소셜 로그인 콜백(OAuth2LoginSuccessHandler)도 더 이상
// 세션에 대상 유저를 미리 심어두지 않고 그냥 이 화면으로 보내기만 한다.
@Slf4j
@Controller
@RequestMapping("/login/reactivate")
@RequiredArgsConstructor
public class DormantAccountReactivationController {

    private final UserRepository userRepository;
    private final SignupEmailVerificationService emailVerificationService;
    private final SocialLoginSessionSupport socialLoginSessionSupport;

    @GetMapping
    public String form() {
        return "login/reactivate";
    }

    @PostMapping("/code")
    @ResponseBody
    public Map<String, Object> sendCode(@RequestParam String username) {
        Map<String, Object> result = new HashMap<>();
        Optional<User> target = resolveTarget(username);
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
    public String verifyAndReactivate(@RequestParam String username,
                                       @RequestParam String code,
                                       HttpServletRequest request,
                                       HttpServletResponse response,
                                       Model model) {
        Optional<User> target = resolveTarget(username);
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
        socialLoginSessionSupport.loginAs(user, request, response);
        return "redirect:/";
    }

    private Optional<User> resolveTarget(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByUsername(username).filter(u -> u.getStatus() == UserStatus.DORMANT);
    }
}
