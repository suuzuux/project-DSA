package megane6.weplanet.controller;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.ArtistCardView;
import megane6.weplanet.domain.dto.ArtistFollowCardView;
import megane6.weplanet.domain.entity.BoardType;
import megane6.weplanet.domain.entity.Post;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.repository.CommentRepository;
import megane6.weplanet.domain.entity.Comment;
import megane6.weplanet.service.CommentService;
import megane6.weplanet.service.FollowService;
import megane6.weplanet.service.MembershipService;
import megane6.weplanet.service.PostService;
import megane6.weplanet.service.media.BoardMediaService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
@RequiredArgsConstructor
public class CommunityController {

	private final UserRepository userRepository;
	private final PostListModelHelper postListModelHelper;
	private final PostService postService;
	private final CommentService commentService;
	private final CommentRepository commentRepository;
	private final MembershipService membershipService;
	private final PostDetailModelHelper postDetailModelHelper;
	private final AuthenticatedUserResolver userResolver;
	private final BoardMediaService boardMediaService;
	private final FollowService followService;

	@GetMapping({"/community/{artistId}", "/community/{artistId}/highlight"})
	public String highlight(@PathVariable Long artistId, @AuthenticationPrincipal AuthenticatedUser principal, Model model) {
		User artist = populateArtistModel(artistId, principal, model);

		// "Fan Posts" 위젯 - 이 커뮤니티 팬 게시판 최신 게시글 상위 4개 + 댓글 수/대표 이미지
		List<Post> fanPosts = postService.getRecentPosts(BoardType.FAN, artist);
		Map<Long, Long> fanPostCommentCounts = new HashMap<>();
		Map<Long, String> fanPostThumbnails = new HashMap<>();
		for (Post post : fanPosts) {
			fanPostCommentCounts.put(post.getId(), commentService.getCommentCount(post));
			postService.getAttachments(post).stream()
					.filter(a -> a.isImage())
					.findFirst()
					.ifPresent(a -> fanPostThumbnails.put(post.getId(), a.getStoredName()));
		}
		model.addAttribute("fanPosts", fanPosts);
		model.addAttribute("fanPostCommentCounts", fanPostCommentCounts);
		model.addAttribute("fanPostThumbnails", fanPostThumbnails);

		// "Comments by 아티스트" 위젯 - 이 아티스트가 작성한 댓글 최신 4개
		List<Comment> artistComments = commentRepository.findTop4ByAuthorOrderByCreatedAtDesc(artist);
		model.addAttribute("artistCommentsWidget", artistComments);

		// "From 아티스트" 위젯 - 이 커뮤니티 아티스트 게시판 최신 게시글 상위 4개 + 대표 이미지
		List<Post> artistPosts = postService.getRecentPosts(BoardType.ARTIST, artist);
		Map<Long, String> artistPostThumbnails = new HashMap<>();
		for (Post post : artistPosts) {
			postService.getAttachments(post).stream()
					.filter(a -> a.isImage())
					.findFirst()
					.ifPresent(a -> artistPostThumbnails.put(post.getId(), a.getStoredName()));
		}
		model.addAttribute("artistPosts", artistPosts);
		model.addAttribute("artistPostThumbnails", artistPostThumbnails);

		return "community/highlight";
	}

	@GetMapping("/community/{artistId}/fan")
	public String fan(
			@PathVariable Long artistId,
			@RequestParam(defaultValue = "latest") String sort,
			@RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
			@AuthenticationPrincipal AuthenticatedUser principal,
			Model model
	) {
		User artist = populateArtistModel(artistId, principal, model);
		// 36번: 아티스트로 로그인한 사람이 팬 게시판을 볼 땐 "Hide from Artists" 글을 목록에서 뺌
		postListModelHelper.populate(model, BoardType.FAN, sort, artist, userResolver.isArtist(principal));

		if ("fetch".equals(requestedWith)) {
			return "community/fragments/postList :: postListFragment";
		}
		return "community/fan";
	}

	@GetMapping("/community/{artistId}/fan/{postId}")
	public String fanDetail(
			@PathVariable Long artistId,
			@PathVariable Long postId,
			@AuthenticationPrincipal AuthenticatedUser principal,
			Model model
	) {
		return communityPostDetail(artistId, postId, BoardType.FAN, "fan", principal, model);
	}

	@GetMapping("/community/{artistId}/artist")
	public String artistBoard(
			@PathVariable Long artistId,
			@RequestParam(defaultValue = "latest") String sort,
			@RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
			@AuthenticationPrincipal AuthenticatedUser principal,
			Model model
	) {
		User artist = populateArtistModel(artistId, principal, model);
		postListModelHelper.populate(model, BoardType.ARTIST, sort, artist);

		if ("fetch".equals(requestedWith)) {
			return "community/fragments/postList :: postListFragment";
		}
		return "community/artist";
	}

	@GetMapping("/community/{artistId}/artist/{postId}")
	public String artistDetail(
			@PathVariable Long artistId,
			@PathVariable Long postId,
			@AuthenticationPrincipal AuthenticatedUser principal,
			Model model
	) {
		return communityPostDetail(artistId, postId, BoardType.ARTIST, "artist", principal, model);
	}

	// fanDetail/artistDetail 공통 처리 - 게시판 종류뿐 아니라 "이 커뮤니티 소속 글인지"도 검증함
	// (게시판이 아티스트별로 분리되기 전엔 다른 커뮤니티 글도 보이던 문제가 있었음)
	private String communityPostDetail(
			Long artistId,
			Long postId,
			BoardType expectedType,
			String boardTab,
			AuthenticatedUser principal,
			Model model
	) {
		populateArtistModel(artistId, principal, model);

		Post post = postService.getPost(postId);
		if (post.getBoardType() != expectedType) {
			throw new IllegalArgumentException("게시판 종류가 맞지 않습니다.");
		}
		if (post.getArtist() == null || !post.getArtist().getId().equals(artistId)) {
			throw new IllegalArgumentException("이 커뮤니티의 게시글이 아닙니다.");
		}

		User currentUser = userResolver.resolve(principal, 1L);
		postDetailModelHelper.populate(model, post, currentUser);
		model.addAttribute("boardTab", boardTab);

		return "community/post-detail";
	}

	@GetMapping("/community/{artistId}/notice")
	public String notice(@PathVariable Long artistId, @AuthenticationPrincipal AuthenticatedUser principal, Model model) {
		populateArtistModel(artistId, principal, model);
		return "community/notice";
	}

	@GetMapping("/community/{artistId}/media")
	public String media(@PathVariable Long artistId, @AuthenticationPrincipal AuthenticatedUser principal, Model model) {
		populateArtistModel(artistId, principal, model);
		model.addAttribute("mediaList", boardMediaService.list(artistId));
		model.addAttribute("groupId", artistId);
		return "community/media";
	}

	@GetMapping("/community/{artistId}/live")
	public String live(@PathVariable Long artistId, @AuthenticationPrincipal AuthenticatedUser principal, Model model) {
		populateArtistModel(artistId, principal, model);
		return "community/live";
	}

	// Membership 가입하기 버튼 - 로그인한 사람 기준으로 이 아티스트 멤버십에 가입(또는 갱신)
	@PostMapping("/community/{artistId}/membership/join")
	public String joinMembership(
			@PathVariable Long artistId,
			@AuthenticationPrincipal AuthenticatedUser principal,
			Model model
	) {
		if (principal == null) {
			return "redirect:/login";
		}

		User artist = userRepository.findById(artistId)
				.filter(user -> user.getRole() == Role.ARTIST)
				.orElseThrow(() -> new IllegalArgumentException("아티스트를 찾을 수 없습니다."));
		User fan = userResolver.resolve(principal, 1L);

		membershipService.join(fan, artist);

		return "redirect:/community/" + artistId + "/highlight";
	}

	// 와이어프레임 26번: About 위젯의 팔로우/팔로잉 버튼
	@PostMapping("/community/{artistId}/follow")
	public String toggleFollow(
			@PathVariable Long artistId,
			@RequestParam(required = false) Long returnTo,
			@AuthenticationPrincipal AuthenticatedUser principal,
			@RequestHeader(value = "X-Requested-With", required = false) String requestedWith
	) {
		if (principal == null) {
			return "redirect:/login";
		}
		User fan = userResolver.resolve(principal, 1L);
		followService.toggle(fan, artistId);

		// 원래 보고 있던 커뮤니티 페이지로 돌아감 (팔로우 대상 아티스트 페이지로 안 튕기게)
		Long backTo = returnTo != null ? returnTo : artistId;
		return "redirect:/community/" + backTo + "/highlight";
	}

	private User populateArtistModel(Long artistId, AuthenticatedUser principal, Model model) {
		User artist = userRepository.findById(artistId)
				.filter(user -> user.getRole() == Role.ARTIST)
				.orElseThrow(() -> new IllegalArgumentException("아티스트를 찾을 수 없습니다."));

		List<ArtistCardView> artists = userRepository.findByRole(Role.ARTIST).stream()
				.map(ArtistCardView::from)
				.toList();

		model.addAttribute("artist", ArtistCardView.from(artist));
		model.addAttribute("artists", artists);

		// 와이어프레임 26번: About 위젯에 "이 아티스트 말고 다른 아티스트도 팔로우해보세요" 추천 리스트
		User currentUserForFollow = principal != null ? userResolver.resolve(principal, 1L) : null;
		Set<Long> followedIds = followService.getFollowedArtistIds(currentUserForFollow);
		List<ArtistFollowCardView> otherArtists = userRepository.findByRole(Role.ARTIST).stream()
				.filter(user -> !user.getId().equals(artistId))
				.map(user -> ArtistFollowCardView.of(user, followedIds.contains(user.getId())))
				.toList();
		model.addAttribute("otherArtists", otherArtists);

		// 사이드바 Membership 카드 - 로그인한 사람이 이 아티스트 멤버십에 가입돼있는지 여부
		if (principal != null) {
			User currentUser = userResolver.resolve(principal, 1L);
			membershipService.getMembership(currentUser, artist).ifPresent(membership -> {
				model.addAttribute("membershipActive", !membership.isExpired());
				model.addAttribute("membershipExpiresAt", membership.getExpiresAt());
			});
		}

		return artist;
	}
}
