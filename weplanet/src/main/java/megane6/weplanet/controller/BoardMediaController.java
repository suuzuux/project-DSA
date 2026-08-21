package megane6.weplanet.controller;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.service.BoardMediaService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Slf4j
@Getter
@Controller
@RequiredArgsConstructor
@RequestMapping("/board")
public class BoardMediaController {

	private final BoardMediaService service;

	@GetMapping("media")
	public String media() {
		return "boardView/boardMediaView";
	}


}
