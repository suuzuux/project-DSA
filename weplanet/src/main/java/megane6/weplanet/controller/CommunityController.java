package megane6.weplanet.controller;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.ArtistCardView;
import megane6.weplanet.domain.dto.ArtistFollowCardView;
import megane6.weplanet.domain.entity.BoardType;
import megane6.weplanet.domain.entity.Post;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.community.CommunityProfile;
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
import megane6.weplanet.service.community.CommunityJoinService;
import megane6.weplanet.service.media.BoardMediaService;
import megane6.weplanet.service.calendar.ArtistAttendanceService;
import megane6.weplanet.service.portal.PortalManagementService;
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
import java.util.Collections;
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
	private final CommunityJoinService communityJoinService;
	private final ArtistAttendanceService artistAttendanceService;
	private final PortalManagementService portalManagementService;
	
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
		// 목록(fan/artist/notice/media/live)에는 가입자 전용 차단이 걸려 있었는데 상세에는 빠져 있어서,
		// 미가입자도 주소창에 /community/1/fan/2 를 직접 치면 글을 그대로 볼 수 있었음.
		// 목록과 같은 기준으로 로그인 여부 + 커뮤니티 가입 여부를 먼저 확인함
		if (principal == null) {
			return "redirect:/login";
		}
		populateArtistModel(artistId, principal, model);
		if (!hasCommunityAccess(userResolver.resolve(principal, 1L), artistId)) {
			model.addAttribute("gatedTab", boardTab);
			return "community/membership-required";
		}
		
		Post post = postService.getPost(postId);
		if (post.getBoardType() != expectedType) {
			throw new IllegalArgumentException("게시판 종류가 맞지 않습니다.");
		}
		if (post.getArtist() == null || !post.getArtist().getId().equals(artistId)) {
			throw new IllegalArgumentException("이 커뮤니티의 게시글이 아닙니다.");
		}
		
		// 36번(Hide from Artists) 필터가 목록에만 있고 상세엔 빠져 있어서,
		// 아티스트가 주소창에 /community/1/fan/5 를 직접 치면 숨긴 글이 그대로 열렸음.
		// 가입자 차단이 목록에만 있던 것과 똑같은 종류의 누락. 목록과 같은 기준을 상세에도 적용함
		if (post.isHiddenFromArtist() && userResolver.isArtist(principal)) {
			throw new IllegalArgumentException("작성자가 아티스트에게 공개하지 않은 게시글입니다.");
		}
		
		User currentUser = userResolver.resolve(principal, 1L);
		postDetailModelHelper.populate(model, post, currentUser);
		model.addAttribute("boardTab", boardTab);
		
		return "community/post-detail";
	}
	
	@GetMapping("/community/{artistId}/notice")
	public String notice(@PathVariable Long artistId, @AuthenticationPrincipal AuthenticatedUser principal, Model model) {
		if (principal == null) {
			return "redirect:/login";
		}
		User artist = populateArtistModel(artistId, principal, model);
		if (!hasCommunityAccess(userResolver.resolve(principal, 1L), artistId)) {
			model.addAttribute("gatedTab", "notice");
			return "community/membership-required";
		}
		model.addAttribute("notices", portalManagementService.getPublishedNotices(artist));
		return "community/notice";
	}

	@GetMapping("/community/{artistId}/notice/{noticeId}")
	public String noticeDetail(@PathVariable Long artistId,
							   @PathVariable Long noticeId,
							   @AuthenticationPrincipal AuthenticatedUser principal,
							   Model model) {
		if (principal == null) {
			return "redirect:/login";
		}
		User artist = populateArtistModel(artistId, principal, model);
		if (!hasCommunityAccess(userResolver.resolve(principal, 1L), artistId)) {
			model.addAttribute("gatedTab", "notice");
			return "community/membership-required";
		}
		model.addAttribute("notice", portalManagementService.getPublishedNotice(artist, noticeId));
		return "community/notice-detail";
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

	@GetMapping("/community/{artistId}/media/{mediaId}")
	public String mediaDetail(@PathVariable Long artistId,
							  @PathVariable Long mediaId,
							  @AuthenticationPrincipal AuthenticatedUser principal,
							  Model model) {
		if (principal == null) {
			return "redirect:/login";
		}
		populateArtistModel(artistId, principal, model);
		if (!hasCommunityAccess(userResolver.resolve(principal, 1L), artistId)) {
			model.addAttribute("gatedTab", "media");
			return "community/membership-required";
		}
		model.addAttribute("mediaPost", boardMediaService.getInCommunity(mediaId, artistId));
		model.addAttribute("groupId", artistId);
		return "community/media-detail";
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
	// 화면에 뜨는 이름은 계정 아이디가 아니라 populateArtistModel이 넣어준 myCommunityProfile(닉네임)을 쓴다.
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
		if (!hasCommunityAccess(me, artistId)) {
			model.addAttribute("gatedTab", "profile");
			return "community/membership-required";
		}
		
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
	// 커뮤니티 "가입"과는 별개다. 가입은 /community/{id}/join (닉네임 필요), 여기는 순수 팔로우.
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
	
	// Fan/Artist/Media/Live/Notice 탭 접근 제어: 로그인은 각 라우트에서 먼저 체크하고,
	// 여기서는 "이 커뮤니티에 가입(CommunityMember)했는지"만 확인함.
	// 예전엔 Follow 기준이었는데, 검색/커뮤니티 페이지 어디서 가입하든 닉네임을 받도록 통일하면서
	// 가입 여부의 기준도 CommunityMember로 옮겼음 (Follow는 About 위젯의 팔로우 버튼 전용으로 남김).
	// 주의: 멤버십(유료, DM 전용)과는 별개 개념 - 헷갈려서 처음엔 membershipActive로 잘못 체크했었음
	private boolean hasCommunityAccess(User currentUser, Long artistId) {
		// 커뮤니티 주인(그 아티스트 본인)은 가입 절차 없이 항상 열람 가능해야 함.
		// 아티스트는 팬 전용 가입 절차를 밟을 수 없어서, 가입 여부만 보면
		// 정작 본인이 자기 게시판에서 차단당하는 문제가 있었음
		if (currentUser.getId().equals(artistId)) {
			return true;
		}
		// 관리자는 신고 처리 등을 위해 전체 열람이 필요함
		if (currentUser.getRole() == Role.ADMIN) {
			return true;
		}
		return communityJoinService.isJoined(currentUser, artistId);
	}
	
	// 멤버십 가입/해지는 팬만 할 수 있는 행동임. 화면에서도 hasRole(FAN)으로 버튼을 숨기지만,
	// 버튼을 숨기는 것만으로는 폼을 직접 호출하는 걸 막을 수 없어서 서버에서도 한 번 더 검증함
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
		if (currentUserForFollow != null
				&& currentUserForFollow.getRole() == Role.ARTIST
				&& currentUserForFollow.getId().equals(artist.getId())) {
			artistAttendanceService.recordVisitIfArtist(currentUserForFollow);
		}
		model.addAttribute("artistAttendance", artistAttendanceService.getAllPawColors(artist));
		Set<Long> followedIds = followService.getFollowedArtistIds(currentUserForFollow);
		List<ArtistFollowCardView> otherArtists = userRepository.findByRole(Role.ARTIST).stream()
				.filter(user -> !user.getId().equals(artistId))
				.map(user -> ArtistFollowCardView.of(user, followedIds.contains(user.getId())))
				.toList();
		model.addAttribute("otherArtists", otherArtists);
		
		// 가입 여부(버튼/배너/드로어)는 CommunityMember 기준. 프로필은 닉네임 표시용.
		Map<Long, CommunityProfile> joinedProfiles = currentUserForFollow != null
				? communityJoinService.joinedProfilesByArtistId(currentUserForFollow)
				: Collections.emptyMap();
		Set<Long> joinedArtistIds = communityJoinService.joinedArtistIds(currentUserForFollow);
		List<ArtistCardView> joinedArtists = artists.stream()
				.filter(a -> joinedArtistIds.contains(a.id()))
				.toList();
		model.addAttribute("joinedArtists", joinedArtists);
		model.addAttribute("communityJoined", joinedArtistIds.contains(artistId));
		model.addAttribute("myCommunityProfile", joinedProfiles.get(artistId));
		
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
