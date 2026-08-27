package megane6.weplanet.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.dto.ArtistCardView;
import megane6.weplanet.domain.dto.RisingCommunityCardView;
import megane6.weplanet.domain.entity.ArtistGroup;
import megane6.weplanet.domain.entity.Post;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.community.CommunityProfile;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.repository.ArtistGroupRepository;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.FollowService;
import megane6.weplanet.service.PostService;
import megane6.weplanet.service.community.CommunityJoinService;
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
	private final ArtistGroupRepository artistGroupRepository;
	private final FollowService followService;
	private final AuthenticatedUserResolver userResolver;
	private final CommunityJoinService communityJoinService;
	
	@GetMapping({"", "/"})
	public String home(@AuthenticationPrincipal AuthenticatedUser principal, Model model) {
		List<User> artistUsers = userRepository.findByRole(Role.ARTIST);
		List<ArtistCardView> artists = artistUsers.stream()
				.map(ArtistCardView::from)
				.toList();
		model.addAttribute("artists", artists);
		
		Map<Long, CommunityProfile> joinedProfiles;
		if (principal != null) {
			User fan = userResolver.resolve(principal, 1L);
			joinedProfiles = communityJoinService.joinedProfilesByArtistId(fan);
		} else {
			joinedProfiles = Collections.emptyMap();
		}
		model.addAttribute("joinedProfiles", joinedProfiles);
		
		// 드로어 메뉴 "커뮤니티 바로가기" - 전체 아티스트가 아니라 실제로 가입한 커뮤니티만 보여주기 위한 목록
		List<ArtistCardView> joinedArtists = artists.stream()
				.filter(a -> joinedProfiles.containsKey(a.id()))
				.toList();
		model.addAttribute("joinedArtists", joinedArtists);
		
		// 와이어프레임 10번: 급상승 커뮤니티 - 데뷔일 + 가입자수(팔로워 수)
		List<RisingCommunityCardView> risingCommunities = artistUsers.stream()
				.map(user -> {
					var debutDate = artistGroupRepository.findById(user.getId())
							.map(ArtistGroup::getDebutDate)
							.orElse(null);
					long followerCount = followService.countFollowers(user.getId());
					return RisingCommunityCardView.of(user, debutDate, followerCount);
				})
				.toList();
		model.addAttribute("risingCommunities", risingCommunities);
		
		// 메인 페이지 "최신 인기 포스트" 위젯 - 게시판 구분 없이 인기순 상위 4개 + 각 게시글 대표 이미지(있으면)
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