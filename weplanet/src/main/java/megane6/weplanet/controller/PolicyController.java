package megane6.weplanet.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// [약관 및 정책] 설정 화면(settings.html)의 "개인정보처리방침"/"이용약관" 링크가 여기로 들어온다.
// 예전엔 onclick으로 "(목업)" 알림창만 띄웠는데, 실제 페이지(policy/privacy, policy/terms)로 교체했다.
// 로그인 여부와 무관하게 볼 수 있어야 해서 SecurityConfig의 PUBLIC_URLS에 /policy/** 를 등록해뒀다.
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
