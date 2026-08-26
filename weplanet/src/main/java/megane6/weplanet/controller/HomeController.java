package megane6.weplanet.controller;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.dto.ArtistCardView;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.repository.UserRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Slf4j
@Controller
@RequiredArgsConstructor
public class HomeController {

	private final UserRepository userRepository;

	@GetMapping({"","/"})
	public String home(Model model) {
		model.addAttribute("artists", userRepository.findByRole(Role.ARTIST).stream()
				.map(ArtistCardView::from)
				.toList());
		return "mainHome";
	}

}
