package megane6.weplanet.controller.common;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// 개인정보처리방침·이용약관 페이지 (로그인 없이 열람).
@Controller
public class PolicyController {

	@GetMapping("/policy/privacy")
	public String privacy() {
		return "policy/privacy";
	}

	@GetMapping("/policy/terms")
	public String terms() {
		return "policy/terms";
	}
}
