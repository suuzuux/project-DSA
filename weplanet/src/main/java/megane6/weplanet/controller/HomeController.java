package megane6.weplanet.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.dto.ArtistCardView;
import megane6.weplanet.domain.entity.Post;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.repository.community.CommunityMemberRepository;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.PostService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class HomeController {
	
	private final UserRepository userRepository;
	private final PostService postService;
	private final CommunityMemberRepository communityMemberRepository;
	private final AuthenticatedUserResolver userResolver;
	
	@GetMapping({"", "/"})
	public String home(@AuthenticationPrincipal AuthenticatedUser principal, Model model) {
		// "커뮤니티 탐색" 그리드/마퀴에 쓰는 전체 아티스트 - 그대로 유지 (건드리면 안 됨)
		List<ArtistCardView> artists = userRepository.findByRole(Role.ARTIST).stream()
				.map(ArtistCardView::from)
				.toList();
		model.addAttribute("artists", artists);
		
		// EXPLORE-05: 햄버거 드로어("커뮤니티 바로가기")에는 이것만 넘김 - 가입한 커뮤니티만
		List<ArtistCardView> joinedArtists;
		if (principal != null) {
			User fan = userResolver.resolve(principal, 1L);
			joinedArtists = communityMemberRepository.findJoinedArtists(fan).stream()
					.map(ArtistCardView::from)
					.toList();
		} else {
			joinedArtists = Collections.emptyList();
		}
		model.addAttribute("joinedArtists", joinedArtists);
		
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