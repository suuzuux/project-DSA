package megane6.weplanet.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.dto.ArtistCardView;
import megane6.weplanet.domain.dto.RisingCommunityCardView;
import megane6.weplanet.domain.entity.ArtistGroup;
import megane6.weplanet.domain.entity.GroupMember;
import megane6.weplanet.domain.entity.Post;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.repository.community.CommunityMemberRepository;
import megane6.weplanet.repository.GroupMemberRepository;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.FollowService;
import megane6.weplanet.service.PostService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
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
	private final GroupMemberRepository groupMemberRepository;
	private final FollowService followService;
	
	@GetMapping({"", "/"})
	public String home(@AuthenticationPrincipal AuthenticatedUser principal, Model model) {
		// "커뮤니티 탐색" 그리드/마퀴에 쓰는 전체 아티스트 - 그대로 유지 (건드리면 안 됨)
		List<User> artistUsers = userRepository.findByRole(Role.ARTIST);
		List<ArtistCardView> artists = artistUsers.stream()
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
		
		// EXPLORE-01: 급상승 커뮤니티 - group_members를 거쳐서 실제 소속 그룹을 찾은 뒤 데뷔일/팔로워 수 계산
		// (예전 코드는 artistGroupRepository.findById(user.getId())처럼 유저 id를 그룹 id인 것처럼 넣는
		//  버그가 있었음 - group_members 매핑 테이블을 거치도록 수정함)
		List<RisingCommunityCardView> risingCommunities = artistUsers.stream()
				.map(user -> {
					ArtistGroup group = groupMemberRepository.findFirstByArtist_IdAndLeftAtIsNull(user.getId())
							.map(GroupMember::getGroup)
							.orElse(null);
					LocalDate debutDate = group != null ? group.getDebutDate() : null;
					long followerCount = group != null ? followService.countFollowers(group.getId()) : 0L;
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