package megane6.weplanet.controller;


import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Slf4j
@Controller
public class HomeController {

	// fanId : 로그인 기능이 아직 없어서, DM 위젯(플로팅 채팅)에서 "지금 나는 몇 번 팬 계정인가"를
	// 임시로 알려주기 위한 테스트용 파라미터. 예: /?fanId=1 (기본값도 1)
	@GetMapping({"","/"})
	public String home(@RequestParam(defaultValue = "1") Long fanId, Model model) {
		model.addAttribute("fanId", fanId);
		return "mainHome";
	}

}
