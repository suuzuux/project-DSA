package megane6.weplanet.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.UserStatus;
import megane6.weplanet.i18n.PreferredLocaleResolver;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.security.SocialLoginSessionSupport;
import megane6.weplanet.service.email.SignupEmailVerificationService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import megane6.weplanet.service.email.SignupEmailVerificationService.VerificationResult;
import megane6.weplanet.service.email.VerificationPurpose;
import megane6.weplanet.service.email.VerificationRateLimitException;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.LocaleResolver;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

// 휴면 해제 - 이메일 인증코드 확인 후 해제하고 바로 로그인시킨다.
@Slf4j
@Controller
@RequestMapping("/login/reactivate")
@RequiredArgsConstructor
public class DormantAccountReactivationController {

    private final UserRepository userRepository;
    private final SignupEmailVerificationService emailVerificationService;
    private final SocialLoginSessionSupport socialLoginSessionSupport;
    private final MessageSource messageSource;
    private final megane6.weplanet.i18n.Messages messages;
    private final LocaleResolver localeResolver;

    private String msg(String code) {
        return messageSource.getMessage(code, null, LocaleContextHolder.getLocale());
    }

    @GetMapping
    public String form() {
        return "login/reactivate";
    }

    @PostMapping("/code")
    @ResponseBody
    public Map<String, Object> sendCode(@RequestParam String username, HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        if (username == null || username.isBlank()) {
            result.put("success", false);
            result.put("message", msg("reactivate.usernameRequired"));
            return result;
        }
        // 휴면 여부가 드러나지 않게 항상 같은 문구로 응답한다.
        Optional<User> target = resolveTarget(username);
        try {
            emailVerificationService.sendVerificationCodeIfEligible(session, VerificationPurpose.REACTIVATE,
                    target.map(User::getEmail).orElse("reactivate:" + username.trim()), target.map(User::getEmail).orElse(null));
            result.put("success", true);
            result.put("message", msg("reactivate.codeSent"));
        } catch (VerificationRateLimitException e) {
            result.put("success", false);
            result.put("message", messages.resolve(e));
        } catch (Exception e) {
            log.error("[휴면계정 해제] 인증코드 발송 실패", e);
            result.put("success", false);
            result.put("message", msg("reactivate.codeSendFailed"));
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
            // 휴면 여부가 드러나지 않게 같은 오류 문구를 쓴다.
            model.addAttribute("errorMessage", messages.resolve(VerificationResult.INVALID.failureMessage()));
            return "login/reactivate";
        }
        User user = target.get();
        // 코드를 보낸 세션에서만 확인된다.
        VerificationResult verified = emailVerificationService.verifyCode(
                request.getSession(false), VerificationPurpose.REACTIVATE, user.getEmail(), code);
        if (!verified.isSuccess()) {
            model.addAttribute("errorMessage", messages.resolve(verified.failureMessage()));
            return "login/reactivate";
        }
        emailVerificationService.clear(request.getSession(false), VerificationPurpose.REACTIVATE, user.getEmail());
        user.reactivate();
        socialLoginSessionSupport.loginAs(user, request, response);
        // 화면 언어를 계정의 선호 언어로 맞춘다.
        localeResolver.setLocale(request, response, PreferredLocaleResolver.toLocale(user.getPreferredLanguage()));
        return "redirect:/";
    }

    private Optional<User> resolveTarget(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByUsername(username).filter(u -> u.getStatus() == UserStatus.DORMANT);
    }
}
