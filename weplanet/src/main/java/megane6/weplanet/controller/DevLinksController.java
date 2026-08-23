package megane6.weplanet.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// 화평님 개인 테스트용 링크 모음 화면 (다른 팀원 화면과 겹치지 않는 별도 파일)
@Controller
public class DevLinksController {

    @GetMapping("/dev/links")
    public String links() {
        return "devTestLinks";
    }
}
