package megane6.weplanet.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.dto.ArtistCardView;
import megane6.weplanet.domain.dto.RisingCommunityCardView;
import megane6.weplanet.domain.dto.event.HashtagEventPageView;
import megane6.weplanet.domain.entity.ArtistGroup;
import megane6.weplanet.domain.entity.Post;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.community.CommunityMember;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.repository.ArtistGroupRepository;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.PostService;
import megane6.weplanet.service.calendar.ArtistAttendanceService;
import megane6.weplanet.service.community.CommunityJoinService;
import megane6.weplanet.service.event.HashtagEventPageService;
import megane6.weplanet.service.portal.PortalManagementService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.*;

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
	private final megane6.weplanet.service.MainBannerTranslator mainBannerTranslator; // 배너 제목·본문을 화면 언어로 (AI 번역)
	private final HashtagEventPageService hashtagEventPageService; // [해시태그 총공] 홈 캐러셀 맨 앞 자동 슬라이드
	
	@GetMapping({"", "/"})
	public String home(@AuthenticationPrincipal AuthenticatedUser principal, Model model) {
		if (principal != null) {
			artistAttendanceService.recordVisitIfArtist(userResolver.resolve(principal, 1L));
		}

		List<User> artistUsers = userRepository.findByRole(Role.ARTIST);
		List<ArtistCardView> artists = portalManagementService.toArtistCards(artistUsers);
		model.addAttribute("artists", artists);
		
		// [해시태그 총공] 진행 중(또는 예정·결과 발표) 총공이 있으면 캐러셀 맨 앞에 붙는 슬라이드 - 제목은 메인 배너와 한 묶음으로 번역
		Optional<HashtagEventPageView> eventView = hashtagEventPageService.getHomeBanner();
		String eventTitle = eventView.map(view -> view.dashboard().title()).orElse(null);
		// 상단 배너 - 최고관리자가 [배너 영역 관리]에서 노출 중으로 둔 배너. 비어 있으면 화면이 기본 배너를 보여준다
		var mainBanners = mainBannerTranslator.localize(mainBannerService.activeSlides(), eventTitle);
		model.addAttribute("mainBanners", mainBanners.slides());
		model.addAttribute("mainBannersPending", mainBanners.pending()); // true 면 화면이 번역문을 다시 받아 바꿔 끼운다
		model.addAttribute("hashtagBanner", eventView.map(view -> hashtagEventPageService.toHomeBanner(view, mainBanners.eventTitle(), mainBanners.eventTitleReady())).orElse(null));
		Map<Long, CommunityMember> joinedProfiles;
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
		
		// 급상승 커뮤니티: 최근 7일 신규 가입자가 많은 순으로 상위 5개 (같으면 전체 가입자 많은 순)
		// 카드에 보이는 가입자 수는 Follow가 아니라 실제 CommunityMember 기준 전체 인원
		Map<Long, Long> newMemberCounts = communityJoinService.countNewMembers(
				artistUsers.stream().map(User::getId).toList(), 7);
		List<RisingCommunityCardView> risingCommunities = artistUsers.stream()
				.map(user -> {
					var debutDate = artistGroupRepository.findById(user.getId())
							.map(ArtistGroup::getDebutDate)
							.orElse(null);
					long memberCount = communityJoinService.countMembers(user.getId());
					return RisingCommunityCardView.of(user, debutDate, memberCount);
				})
				.sorted(Comparator
						.comparingLong((RisingCommunityCardView card) -> newMemberCounts.getOrDefault(card.id(), 0L))
						.thenComparingLong(RisingCommunityCardView::followerCount)
						.reversed())
				.limit(5)
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
