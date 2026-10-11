package megane6.weplanet.controller.common;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Map;

// 정책 페이지 (로그인 없이 열람).
@Controller
public class PolicyController {
	private static final Map<String, String> SAMPLE_HEADINGS = Map.of(
			"operation", "footer.operationPolicy",
			"paid", "footer.paidTerms",
			"youth", "footer.youthPolicy",
			"illegal-content", "footer.illegalContentReport",
			"cookies", "footer.cookiePolicy",
			"cookie-settings", "footer.cookieSettings");

	@GetMapping("/policy/privacy")
	public String privacy(@RequestParam(name = "from", defaultValue = "home") String from, Model model) {
		model.addAttribute("policyFromSettings", "settings".equals(from));
		return "policy/privacy";
	}

	@GetMapping("/policy/terms")
	public String terms(@RequestParam(name = "from", defaultValue = "home") String from, Model model) {
		model.addAttribute("policyFromSettings", "settings".equals(from));
		return "policy/terms";
	}

	@GetMapping("/policy/{page}")
	public String sample(@PathVariable String page, Locale locale, Model model) {
		String heading = SAMPLE_HEADINGS.get(page);
		if (heading == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		}
		String language = switch (locale.getLanguage()) {
			case "ja", "en" -> locale.getLanguage();
			default -> "ko";
		};
		model.addAttribute("policyType", page);
		model.addAttribute("policyHeading", heading);
		model.addAttribute("policyFragment", page + "-" + language);
		return "policy/sample";
	}
}
