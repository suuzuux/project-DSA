package megane6.weplanet.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.enumfolder.Language;
import megane6.weplanet.i18n.PreferredLocaleResolver;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.LocaleResolver;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class LanguageController {

	private final UserRepository userRepository;
	private final UserService userService;
	private final LocaleResolver localeResolver;

	@PostMapping("/language")
	@ResponseBody
	public Map<String, Object> updateLanguage(@AuthenticationPrincipal AuthenticatedUser principal,
											  @RequestParam Language language,
											  HttpServletRequest request, HttpServletResponse response) {
		Map<String, Object> result = new HashMap<>();
		if (principal != null) {
			userRepository.findOneById(principal.getId())
					.ifPresent(user -> userService.updateLanguage(user, language));
		}
		localeResolver.setLocale(request, response, PreferredLocaleResolver.toLocale(language));
		result.put("success", true);
		return result;
	}
}
