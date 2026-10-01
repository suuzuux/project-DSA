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
        Optional<User> target = resolveTarget(username);
        if (target.isEmpty()) {
            result.put("success", false);
            result.put("message", msg("reactivate.accountNotFound"));
            return result;
        }
        try {
            emailVerificationService.sendVerificationCode(session, VerificationPurpose.REACTIVATE, target.get().getEmail());
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
            model.addAttribute("errorMessage", msg("reactivate.accountNotFound"));
            return "login/reactivate";
        }
        User user = target.get();
        // 코드를 보낸 것과 같은 세션에서만 확인된다 - 휴면 해제는 성공하면 바로 로그인되므로 특히 중요
        VerificationResult verified = emailVerificationService.verifyCode(
                request.getSession(false), VerificationPurpose.REACTIVATE, user.getEmail(), code);
        if (!verified.isSuccess()) {
            model.addAttribute("errorMessage", messages.resolve(verified.failureMessage()));
            return "login/reactivate";
        }
        emailVerificationService.clear(request.getSession(false), VerificationPurpose.REACTIVATE, user.getEmail());
        user.reactivate();
        socialLoginSessionSupport.loginAs(user, request, response);
        // SETTINGS-03 로케일 버그#2 유형 수정: 휴면계정 해제도 로그인을 새로 여는 지점이라, 다른
        // 로그인 성공 핸들러들과 동일하게 세션 로케일을 DB에 저장된 선호 언어로 맞춰준다. 이게 없으면
        // 재활성화 직후 화면이 재로그인 전까지 계속 한국어로 나온다.
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
