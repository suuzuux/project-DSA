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
import megane6.weplanet.repository.LikeRepository;
import megane6.weplanet.repository.BookmarkRepository;
import megane6.weplanet.domain.entity.Comment;
import megane6.weplanet.domain.entity.Like;
import megane6.weplanet.domain.entity.Bookmark;
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
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.format.DateTimeFormatter;
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
	private final LikeRepository likeRepository;
	private final BookmarkRepository bookmarkRepository;
	private final MembershipService membershipService;
	private final PostDetailModelHelper postDetailModelHelper;
	private final AuthenticatedUserResolver userResolver;
	private final BoardMediaService boardMediaService;
	// [머지 충돌 해결] main에서 포털(Portal) 기능이 되돌려지면서 PortalManagementService 클래스 자체가
	// 삭제됨 -> portalManagementService 필드도 함께 제거 (남기면 타입을 못 찾아 컴파일 실패)
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
		if (principal == null) {
			return "redirect:/login";
		}
		User artist = populateArtistModel(artistId, principal, model);
		if (!hasCommunityAccess(userResolver.resolve(principal, 1L), artistId)) {
			model.addAttribute("gatedTab", "fan");
			return "community/membership-required";
		}
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
		if (principal == null) {
			return "redirect:/login";
		}
		User artist = populateArtistModel(artistId, principal, model);
		if (!hasCommunityAccess(userResolver.resolve(principal, 1L), artistId)) {
			model.addAttribute("gatedTab", "artist");
			return "community/membership-required";
		}
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
		// [머지 충돌 해결] HEAD의 로그인/커뮤니티 가입 접근 제어는 유지.
		// 단, 공지 목록 조회(portalManagementService.getPublishedNotices)는 main에서 포털 기능이
		// 되돌려지며 서비스 클래스가 삭제되어 제거함 -> 현재 공지 탭은 "등록된 공지가 없습니다"로 표시됨
		if (principal == null) {
			return "redirect:/login";
		}
		populateArtistModel(artistId, principal, model);
		if (!hasCommunityAccess(userResolver.resolve(principal, 1L), artistId)) {
			model.addAttribute("gatedTab", "notice");
			return "community/membership-required";
		}
		return "community/notice";
	}

	@GetMapping("/community/{artistId}/media")
	public String media(@PathVariable Long artistId, @AuthenticationPrincipal AuthenticatedUser principal, Model model) {
		if (principal == null) {
			return "redirect:/login";
		}
		populateArtistModel(artistId, principal, model);
		if (!hasCommunityAccess(userResolver.resolve(principal, 1L), artistId)) {
			model.addAttribute("gatedTab", "media");
			return "community/membership-required";
		}
		model.addAttribute("mediaList", boardMediaService.list(artistId));
		model.addAttribute("groupId", artistId);
		return "community/media";
	}

	@GetMapping("/community/{artistId}/live")
	public String live(@PathVariable Long artistId, @AuthenticationPrincipal AuthenticatedUser principal, Model model) {
		if (principal == null) {
			return "redirect:/login";
		}
		populateArtistModel(artistId, principal, model);
		if (!hasCommunityAccess(userResolver.resolve(principal, 1L), artistId)) {
			model.addAttribute("gatedTab", "live");
			return "community/membership-required";
		}
		return "community/live";
	}

	// 와이어프레임 20~23번: 내 프로필 - 댓글/포스트/좋아요/북마크 히스토리
	@GetMapping("/community/{artistId}/profile")
	public String myProfile(
			@PathVariable Long artistId,
			@RequestParam(defaultValue = "latest") String sort,
			@AuthenticationPrincipal AuthenticatedUser principal,
			Model model
	) {
		if (principal == null) {
			return "redirect:/login";
		}
		populateArtistModel(artistId, principal, model);
		User me = userResolver.resolve(principal, 1L);

		boolean oldest = "oldest".equals(sort);

		List<Comment> myComments = oldest
				? commentRepository.findByAuthorOrderByCreatedAtAsc(me)
				: commentRepository.findByAuthorOrderByCreatedAtDesc(me);

		List<Post> myPosts = oldest
				? postService.getPostsByAuthor(me, true)
				: postService.getPostsByAuthor(me, false);
		Map<Long, Long> myPostCommentCounts = new HashMap<>();
		for (Post post : myPosts) {
			myPostCommentCounts.put(post.getId(), commentService.getCommentCount(post));
		}

		List<Post> likedPosts = likeRepository.findByUserOrderByCreatedAtDesc(me).stream()
				.map(Like::getPost)
				.toList();

		List<Post> bookmarkedPosts = bookmarkRepository.findByUserOrderByCreatedAtDesc(me).stream()
				.map(Bookmark::getPost)
				.toList();

		model.addAttribute("myComments", myComments);
		model.addAttribute("myPosts", myPosts);
		model.addAttribute("myPostCommentCounts", myPostCommentCounts);
		model.addAttribute("likedPosts", likedPosts);
		model.addAttribute("bookmarkedPosts", bookmarkedPosts);
		model.addAttribute("myFollowingCount", followService.getFollowedArtistIds(me).size());
		model.addAttribute("sort", sort);

		return "community/profile";
	}

	// Membership 가입하기 버튼 - 로그인한 사람 기준으로 이 아티스트 멤버십에 가입(또는 갱신)
	@PostMapping("/community/{artistId}/membership/join")public String joinMembership(
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
		User fan = requireFan(principal);

		membershipService.join(fan, artist);

		return "redirect:/community/" + artistId + "/highlight";
	}

	// [머지 충돌 해결] main엔 이 기능이 없었음(이전 버전) -> HEAD 유지. 포털과 무관하고 MembershipService는 살아있음
	// 멤버십 해지 (상세보기 모달의 "멤버십 해지" 버튼)
	@PostMapping("/community/{artistId}/membership/cancel")
	public String cancelMembership(
			@PathVariable Long artistId,
			@AuthenticationPrincipal AuthenticatedUser principal
	) {
		if (principal == null) {
			return "redirect:/login";
		}

		User artist = userRepository.findById(artistId)
				.filter(user -> user.getRole() == Role.ARTIST)
				.orElseThrow(() -> new IllegalArgumentException("아티스트를 찾을 수 없습니다."));
		User fan = requireFan(principal);

		membershipService.cancel(fan, artist);

		return "redirect:/community/" + artistId + "/highlight";
	}

	// "Membership 상세보기" 모달(P33) - 목업 데이터(홍길동/고정 날짜) 대신 실제 가입일/만료일/연락처로 채워서 보여줌.
	// 모달 자체는 shell.js가 페이지 공통으로 그려두는 거라 여기서 뷰를 새로 만들지 않고 JSON만 내려줌.
	@GetMapping("/community/{artistId}/membership/detail")
	@ResponseBody
	public Map<String, Object> membershipDetail(
			@PathVariable Long artistId,
			@AuthenticationPrincipal AuthenticatedUser principal
	) {
		Map<String, Object> result = new HashMap<>();
		if (principal == null) {
			return result;
		}

		User artist = userRepository.findById(artistId)
				.filter(user -> user.getRole() == Role.ARTIST)
				.orElse(null);
		if (artist == null) {
			return result;
		}

		User fan = userResolver.resolve(principal, 1L);
		DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy.MM.dd");

		membershipService.getMembership(fan, artist).ifPresent(membership -> {
			result.put("name", fan.getRealName());
			result.put("email", fan.getEmail());
			result.put("phone", fan.getPhone());
			result.put("membershipNo", "WP-" + artistId + "-" + membership.getId());
			result.put("period",
					membership.getCreatedAt().format(dateFormat) + " ~ " + membership.getExpiresAt().format(dateFormat) + " (KST)");
		});

		return result;
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
		User fan = requireFan(principal);
		followService.toggle(fan, artistId);

		// 원래 보고 있던 커뮤니티 페이지로 돌아감 (팔로우 대상 아티스트 페이지로 안 튕기게)
		Long backTo = returnTo != null ? returnTo : artistId;
		return "redirect:/community/" + backTo + "/highlight";
	}

	// [머지 충돌 해결] main엔 없었음(이전 버전) -> HEAD 유지. FollowService 기반이라 포털 삭제와 무관
	// Fan/Artist/Media/Live/Notice 탭 접근 제어: 로그인은 각 라우트에서 먼저 체크하고,
	// 여기서는 "이 커뮤니티에 무료 가입(팔로우)했는지"만 확인함.
	// 주의: 멤버십(유료, DM 전용)과는 별개 개념 - 헷갈려서 처음엔 membershipActive로 잘못 체크했었음
	private boolean hasCommunityAccess(User currentUser, Long artistId) {
		return followService.isFollowing(currentUser, artistId);
	}

	// 커뮤니티 가입(팔로우)과 멤버십 가입/해지는 "팬"만 할 수 있는 행동임.
	// 화면(layout.html)에서도 sec:authorize="hasRole('FAN')"로 버튼을 숨기지만,
	// 버튼을 숨기는 것만으로는 폼을 직접 호출하는 걸 막을 수 없어서 서버에서도 한 번 더 검증함.
	// (아티스트가 자기 커뮤니티에 가입되거나, 관리자/소속사 계정에 멤버십이 생기는 걸 방지)
	private User requireFan(AuthenticatedUser principal) {
		User user = userResolver.resolve(principal, 1L);
		if (user.getRole() != Role.FAN) {
			throw new IllegalStateException("팬 계정만 이용할 수 있는 기능입니다.");
		}
		return user;
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

		// [머지 충돌 해결] main엔 없었음(이전 버전) -> HEAD 유지
		// 와이어프레임 26번: About 위젯에 "이 아티스트 말고 다른 아티스트도 팔로우해보세요" 추천 리스트
		User currentUserForFollow = principal != null ? userResolver.resolve(principal, 1L) : null;
		Set<Long> followedIds = followService.getFollowedArtistIds(currentUserForFollow);
		List<ArtistFollowCardView> otherArtists = userRepository.findByRole(Role.ARTIST).stream()
				.filter(user -> !user.getId().equals(artistId))
				.map(user -> ArtistFollowCardView.of(user, followedIds.contains(user.getId())))
				.toList();
		model.addAttribute("otherArtists", otherArtists);

		// [머지 충돌 해결] main엔 없었음(이전 버전) -> HEAD 유지
		// 이 커뮤니티에 무료 가입(팔로우)했는지 - Fan/Artist/Media/Live/Notice 접근 제어 및 하단 배너 표시에 씀
		// (followedIds는 바로 위에서 다른 아티스트 추천용으로 이미 조회해둔 걸 재사용)
		model.addAttribute("communityJoined", followedIds.contains(artistId));

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
