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
import megane6.weplanet.service.PostService;
import megane6.weplanet.service.community.CommunityJoinService;
import megane6.weplanet.service.portal.PortalManagementService;
import megane6.weplanet.service.calendar.ArtistAttendanceService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Controller
@RequiredArgsConstructor
public class HomeController {
	
	private final UserRepository userRepository;
	private final PostService postService;
	private final ArtistGroupRepository artistGroupRepository;
	private final AuthenticatedUserResolver userResolver;
	private final CommunityJoinService communityJoinService;
	private final megane6.weplanet.service.community.CommunityDrawerHelper communityDrawerHelper;
	private final ArtistAttendanceService artistAttendanceService;
	private final PortalManagementService portalManagementService;
	private final megane6.weplanet.service.MainBannerService mainBannerService;
	
	@GetMapping({"", "/"})
	public String home(@AuthenticationPrincipal AuthenticatedUser principal, Model model) {
		if (principal != null) {
			artistAttendanceService.recordVisitIfArtist(userResolver.resolve(principal, 1L));
		}

		List<User> artistUsers = userRepository.findByRole(Role.ARTIST);
		List<ArtistCardView> artists = portalManagementService.toArtistCards(artistUsers);
		model.addAttribute("artists", artists);
		// 상단 배너 - 최고관리자가 [배너 영역 관리]에서 노출 중으로 둔 배너. 비어 있으면 화면이 기본 배너를 보여준다
		model.addAttribute("mainBanners", mainBannerService.activeSlides());
		
		Map<Long, CommunityProfile> joinedProfiles;
		Set<Long> joinedArtistIds;
		User viewer = null;
		if (principal != null) {
			viewer = userResolver.resolve(principal, 1L);
			joinedProfiles = communityJoinService.joinedProfilesByArtistId(viewer);
			joinedArtistIds = communityJoinService.joinedArtistIds(viewer);
		} else {
			joinedProfiles = Collections.emptyMap();
			joinedArtistIds = Collections.emptySet();
		}
		model.addAttribute("joinedProfiles", joinedProfiles);
		
		// 햄버거: 상단(가입/내 커뮤니티) + 하단(모든 커뮤니티)
		model.addAttribute("joinedArtists",
				communityDrawerHelper.joined(viewer, artists, joinedArtistIds));
		model.addAttribute("otherCommunities",
				communityDrawerHelper.otherCommunities(viewer, artists));
		
		// 급상승 커뮤니티 카드의 가입자 수는 Follow가 아니라 실제 CommunityMember 기준
		List<RisingCommunityCardView> risingCommunities = artistUsers.stream()
				.map(user -> {
					var debutDate = artistGroupRepository.findById(user.getId())
							.map(ArtistGroup::getDebutDate)
							.orElse(null);
					long memberCount = communityJoinService.countMembers(user.getId());
					return RisingCommunityCardView.of(user, debutDate, memberCount);
				})
				.toList();
		model.addAttribute("risingCommunities", risingCommunities);
		// 급상승 카드 링크용 커뮤니티 주소(영문 주소 우선). Thymeleaf에서 Long 키 조회가 어긋나지 않게 문자열 키로 둔다
		Map<String, String> communityHomeUrls = new HashMap<>();
		artists.forEach(card -> communityHomeUrls.put(String.valueOf(card.id()), card.homeUrl()));
		model.addAttribute("communityHomeUrls", communityHomeUrls);
		
		// 메인 페이지 "최신 인기 포스트" 위젯 - 게시판 구분 없이 인기순 상위 4개 + 각 게시글 대표 이미지(있으면)
		// 작성자 표시는 해당 커뮤니티 가입 닉네임 기준
		List<Post> popularPosts = postService.getPopularPosts();
		Map<Long, String> popularPostThumbnails = new HashMap<>();
		Map<String, String> popularAuthorNicknames = new HashMap<>();
		for (Post post : popularPosts) {
			postService.getAttachments(post).stream()
					.filter(a -> a.isImage())
					.findFirst()
					.ifPresent(a -> popularPostThumbnails.put(post.getId(), a.getStoredName()));
			if (post.getAuthor() != null && post.getArtist() != null) {
				popularAuthorNicknames.put(
						String.valueOf(post.getId()),
						communityJoinService.displayNickname(post.getAuthor(), post.getArtist().getId()));
			}
		}
		model.addAttribute("popularPosts", popularPosts);
		model.addAttribute("popularPostThumbnails", popularPostThumbnails);
		model.addAttribute("popularAuthorNicknames", popularAuthorNicknames);
		
		return "index";
	}
	
	@GetMapping("/home")
	public String homeAlias() {
		return "redirect:/";
	}
	
}
