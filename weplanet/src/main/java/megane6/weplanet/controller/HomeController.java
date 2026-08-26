package megane6.weplanet.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.dto.ArtistCardView;
import megane6.weplanet.domain.entity.Post;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.service.PostService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class HomeController {

	private final UserRepository userRepository;
	private final PostService postService;

	@GetMapping({"", "/"})
	public String home(Model model) {
		List<ArtistCardView> artists = userRepository.findByRole(Role.ARTIST).stream()
				.map(ArtistCardView::from)
				.toList();
		model.addAttribute("artists", artists);

		// 메인 페이지 "최신 인기 포스트" - 인기순 상위 4개 + 대표 이미지
		List<Post> popularPosts = postService.getPopularPosts();
		Map<Long, String> popularPostThumbnails = new HashMap<>();
		for (Post post : popularPosts) {
			postService.getAttachments(post).stream()
					.filter(a -> a.isImage())
					.findFirst()
					.ifPresent(a -> popularPostThumbnails.put(post.getId(), a.getStoredName()));
		}
		model.addAttribute("popularPosts", popularPosts);
		model.addAttribute("popularPostThumbnails", popularPostThumbnails);

		return "index";
	}

	@GetMapping("/home")
	public String homeAlias() {
		return "redirect:/";
	}

}
