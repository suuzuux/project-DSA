package megane6.weplanet.controller.common;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// 설정 화면의 "개인정보처리방침"/"이용약관" 페이지.
// 로그인 없이도 볼 수 있다 (SecurityConfig PUBLIC_URLS 의 /policy/**).
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
