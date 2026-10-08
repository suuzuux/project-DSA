package megane6.weplanet.controller.fan.community;
import megane6.weplanet.service.main.ContentTranslationService;
import megane6.weplanet.controller.common.AuthenticatedUserResolver;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.ArtistCardView;
import megane6.weplanet.domain.dto.community.CommunityJoinInfo;
import megane6.weplanet.domain.dto.live.LiveStatusView;
import megane6.weplanet.domain.entity.*;
import megane6.weplanet.domain.entity.community.CommunityMember;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.domain.event.BadgeActivityEvent;
import megane6.weplanet.repository.fan.BookmarkRepository;
import megane6.weplanet.repository.comment.CommentRepository;
import megane6.weplanet.repository.fan.LikeRepository;
import megane6.weplanet.repository.main.UserRepository;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.comment.CommentService;
import megane6.weplanet.service.membership.MembershipService;
import megane6.weplanet.service.fan.PostService;
import megane6.weplanet.service.fan.UserFollowService;
import megane6.weplanet.service.calendar.ArtistAttendanceService;
import megane6.weplanet.service.community.CommunityArtistResolver;
import megane6.weplanet.service.community.CommunityUrls;
import megane6.weplanet.service.community.CommunityJoinService;
import megane6.weplanet.service.live.LiveBroadcastService;
import megane6.weplanet.service.media.BoardMediaService;
import megane6.weplanet.service.portal.PortalManagementService;
import megane6.weplanet.web.RefererRedirects;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

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
	private final UserFollowService userFollowService; // 사람↔사람 팔로우 (팬↔팬, 팬→아티스트 공용)
	private final CommunityJoinService communityJoinService;
	private final megane6.weplanet.service.community.CommunityDrawerHelper communityDrawerHelper;
	private final ArtistAttendanceService artistAttendanceService;
	private final PortalManagementService portalManagementService;
	private final LiveBroadcastService liveBroadcastService;
	
	private final ApplicationEventPublisher eventPublisher; // [배지] 시청 활동 이벤트 발행용
	// flash 예외 메시지를 현재 로케일 문구로 바꾼다.
	private final megane6.weplanet.i18n.Messages messages;
	private final megane6.weplanet.service.main.ContentTranslationService contentTranslationService; // 공지 번역보기 (AI)
	
	private final CommunityArtistResolver communityArtistResolver;

	private final CommunityUrls communityUrls; // 탭/메뉴 링크를 영문 주소(/kiikii/fan)로

	@GetMapping({"/community/{artistId}", "/community/{artistId}/highlight"})
	public String highlight(@PathVariable Long artistId, @AuthenticationPrincipal AuthenticatedUser principal, Model model) {
		User artist = populateArtistModel(artistId, principal, model);
		
		// Fan Posts 위젯 - 팬 게시판 최신 글 6개와 댓글 수·대표 이미지.
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
		// 작성자 닉네임은 커뮤니티 가입 닉네임으로 보여준다.
		model.addAttribute("fanPostAuthorNicknames", communityJoinService.displayNicknamesByAuthorIdKey(
				fanPosts.stream().map(Post::getAuthor).toList(), artistId));
		
		// Comments by 위젯 - 그룹·멤버 계정의 최신 댓글·답글 6개.
		List<Comment> artistComments = commentRepository
				.findTop6ByAuthor_IdInAndPost_Artist_IdAndDeletedAtIsNullOrderByCreatedAtDesc(
						communityArtistResolver.artistSideUserIds(artistId), artistId);
		model.addAttribute("artistCommentsWidget", artistComments);
		
		// From 위젯 - 아티스트 게시판 최신 글 6개와 대표 이미지.
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

		// Lives 위젯 - 최대 3칸 (방송 중이면 LIVE 카드 + 다시보기 최신순).
		boolean liveNow = liveBroadcastService.status(artistId).live();
		model.addAttribute("liveNow", liveNow);
		model.addAttribute("highlightReplays", boardMediaService.listLiveReplays(artistId).stream()
				.filter(replay -> replay.getFirstVideoFileId() != null)
				.limit(liveNow ? 2 : 3)
				.toList());

		Set<Long> likedPostIds = Collections.emptySet();
		if (principal != null) {
			User me = userResolver.resolve(principal, 1L);
			Set<Long> visibleIds = new java.util.HashSet<>();
			fanPosts.forEach(post -> visibleIds.add(post.getId()));
			artistPosts.forEach(post -> visibleIds.add(post.getId()));
			if (!visibleIds.isEmpty()) {
				likedPostIds = new java.util.HashSet<>();
				for (Like like : likeRepository.findByUserOrderByCreatedAtDesc(me)) {
					if (like.getPost() != null && visibleIds.contains(like.getPost().getId())) {
						likedPostIds.add(like.getPost().getId());
					}
				}
			}
		}
		model.addAttribute("likedPostIds", likedPostIds);
		
		return "community/highlight";
	}
	
	@GetMapping("/community/{artistId}/fan")
	public String fan(
			@PathVariable Long artistId,
			@RequestParam(defaultValue = "latest") String sort,
			@RequestParam(defaultValue = "0") int page,
			@RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
			@AuthenticationPrincipal AuthenticatedUser principal,
			Model model
	) {
		if (principal == null) {
			return "redirect:/login";
		}
		User me = userResolver.resolve(principal, 1L);
		User artist = populateArtistModel(artistId, principal, model);
		if (!hasCommunityAccess(me, artistId)) {
			model.addAttribute("gatedTab", "fan");
			return "community/membership-required";
		}
		// 아티스트가 팬 게시판을 볼 땐 Hide from Artists 글을 뺀다.
		postListModelHelper.populateCommunityPage(
				model, BoardType.FAN, sort, artist, userResolver.isArtist(principal), me, page);
		
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
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(required = false) String filter, // "media" 면 사진·영상 첨부 글만

			@RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
			@AuthenticationPrincipal AuthenticatedUser principal,
			Model model
	) {
		if (principal == null) {
			return "redirect:/login";
		}
		User me = userResolver.resolve(principal, 1L);
		User artist = populateArtistModel(artistId, principal, model);
		if (!hasCommunityAccess(me, artistId)) {
			model.addAttribute("gatedTab", "artist");
			return "community/membership-required";
		}
		boolean mediaOnly = "media".equals(filter);
		postListModelHelper.populateCommunityPage(
				model, BoardType.ARTIST, sort, artist, false, me, page, mediaOnly);
		// 전체/사진·미디어 칩 표시와 정렬·더보기 주소에 필터를 유지한다.
		model.addAttribute("boardFilter", mediaOnly ? "media" : null);

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
	
	// 게시판 종류와 이 커뮤니티 소속 글인지를 함께 검증하는 상세 공통 처리.
	private String communityPostDetail(
			Long artistId,
			Long postId,
			BoardType expectedType,
			String boardTab,
			AuthenticatedUser principal,
			Model model
	) {
		// 상세도 목록과 같이 로그인·커뮤니티 가입 여부를 먼저 확인한다.
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
			throw new IllegalArgumentException("error.post.boardMismatch");
		}
		if (post.getArtist() == null || !post.getArtist().getId().equals(artistId)) {
			throw new IllegalArgumentException("error.post.notInCommunity");
		}
		
		// 아티스트에게는 Hide from Artists 글 상세도 막는다.
		if (post.isHiddenFromArtist() && userResolver.isArtist(principal)) {
			throw new IllegalArgumentException("error.post.hiddenFromArtist");
		}
		
		User currentUser = userResolver.resolve(principal, 1L);
		postDetailModelHelper.populate(model, post, currentUser, artistId);
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

	// 커뮤니티 공지 AI 번역 (상세와 같은 권한).
	@PostMapping("/community/{artistId}/notice/{noticeId}/translate")
	@ResponseBody
	public Map<String, Object> translateNotice(@PathVariable Long artistId,
											   @PathVariable Long noticeId,
											   @AuthenticationPrincipal AuthenticatedUser principal) {
		if (principal == null) {
			return Map.of("success", false, "message", messages.get("common.error.loginRequired"));
		}
		User me = userResolver.resolve(principal, 1L);
		if (!hasCommunityAccess(me, artistId)) {
			return Map.of("success", false, "message", messages.get("error.forbidden"));
		}
		User artist = userRepository.findOneById(artistId)
				.filter(user -> user.getRole() == Role.ARTIST)
				.orElseThrow(() -> new IllegalArgumentException("error.community.artistNotFound"));
		var notice = portalManagementService.getPublishedNotice(artist, noticeId);
		return contentTranslationService.noticeResponse(notice.getTitle(), notice.getContent(), me.getPreferredLanguage());
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
		model.addAttribute("mediaList", boardMediaService.listWithoutLiveReplays(artistId, canSeeMembershipMedia(model)));
		model.addAttribute("groupId", artistId);
		Set<Long> likedMediaIds = Collections.emptySet();
		if (principal != null) {
			User me = userResolver.resolve(principal, 1L);
			likedMediaIds = boardMediaService.likedIdsForUser(me, artistId);
		}
		model.addAttribute("likedMediaIds", likedMediaIds);
		return "community/media";
	}

	@GetMapping("/community/{artistId}/media/{mediaId}")
	public String mediaDetail(@PathVariable Long artistId,
							  @PathVariable Long mediaId,
							  @AuthenticationPrincipal AuthenticatedUser principal,
							  Model model,
							  RedirectAttributes redirectAttributes) {
		if (principal == null) {
			return "redirect:/login";
		}
		populateArtistModel(artistId, principal, model);
		if (!hasCommunityAccess(userResolver.resolve(principal, 1L), artistId)) {
			model.addAttribute("gatedTab", "media");
			return "community/membership-required";
		}
		User me = userResolver.resolve(principal, 1L);
		try {
			model.addAttribute("mediaPost", boardMediaService.getInCommunity(mediaId, artistId, canSeeMembershipMedia(model)));
		} catch (IllegalArgumentException e) {
			redirectAttributes.addFlashAttribute("error", messages.resolve(e));
			return "redirect:/community/" + artistId + "/media";
		}
		model.addAttribute("groupId", artistId);
		model.addAttribute("liked", boardMediaService.isLiked(mediaId, me));
		
		// [배지] 미디어 상세를 열면 시청으로 본다.
		eventPublisher.publishEvent(new BadgeActivityEvent(
				me.getId(), artistId, BadgeActivityEvent.Activity.MEDIA_VIEWED
		));
		
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
		LiveStatusView liveStatus = liveBroadcastService.status(artistId);
		model.addAttribute("liveStatus", liveStatus);
		model.addAttribute("liveReplays", boardMediaService.listLiveReplays(artistId));

		// [배지] 방송 중일 때 들어온 경우만 라이브 시청으로 본다.
		if (liveStatus.live()) {
			eventPublisher.publishEvent(new BadgeActivityEvent(
					userResolver.resolve(principal, 1L).getId(), artistId,
					BadgeActivityEvent.Activity.LIVE_VIEWED
			));
		}

		return "community/live";
	}
	
	// 내 프로필 이동 (영문 주소는 필터가 forward 하므로 주소창 유지).
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
		User me = userResolver.resolve(principal, 1L);
		return profile(artistId, me.getId(), sort, principal, model);
	}

	// 프로필 - 댓글·포스트·좋아요·북마크 이력 (본인이면 편집, 타인이면 팔로우).
	@GetMapping("/community/{artistId}/profile/{userId}")
	public String profile(
			@PathVariable Long artistId,
			@PathVariable Long userId,
			@RequestParam(defaultValue = "latest") String sort,
			@AuthenticationPrincipal AuthenticatedUser principal,
			Model model
	) {
		if (principal == null) {
			return "redirect:/login";
		}
		populateArtistModel(artistId, principal, model);
		User me = userResolver.resolve(principal, 1L);
		User targetUser = userRepository.findOneById(userId)
				.orElseThrow(() -> new IllegalArgumentException("error.community.userNotFound"));

		// 나와 상대 모두 이 커뮤니티를 열람할 수 있어야 프로필을 볼 수 있다.
		if (!hasCommunityAccess(me, artistId) || !hasCommunityAccess(targetUser, artistId)) {
			model.addAttribute("gatedTab", "profile");
			return "community/membership-required";
		}

		boolean isOwnProfile = me.getId().equals(userId);
		model.addAttribute("isOwnProfile", isOwnProfile);
		model.addAttribute("profileUserId", userId);
		// 프로필 주인이 이 커뮤니티 아티스트 본인이면 아티스트 팔로우로 동작한다.
		model.addAttribute("isCommunityOwnerProfile", userId.equals(artistId));
		// 아티스트 쪽 계정이면 프로필을 포털 프로필로 그린다.
		boolean targetIsArtistHere = communityArtistResolver.isArtistOf(targetUser, artistId);
		model.addAttribute("isTargetArtistSide", targetIsArtistHere);
		if (targetIsArtistHere) {
			model.addAttribute("targetArtistAvatarUrl", portalManagementService.findLogoImageUrl(targetUser));
			model.addAttribute("targetArtistBackgroundUrl", portalManagementService.findHeaderImageUrl(targetUser));
			model.addAttribute("targetArtistIntro", portalManagementService.findIntro(targetUser));
		}

		// 가입 당일을 D+1로 계산한다 (가입 행이 없는 아티스트는 계정 생성일 기준).
		CommunityJoinInfo communityJoinInfo = communityJoinService.joinInfoOf(targetUser, artistId);
		if (communityJoinInfo == null && targetIsArtistHere && targetUser.getCreatedAt() != null) {
			communityJoinInfo = CommunityJoinInfo.from(targetUser.getCreatedAt(), LocalDate.now());
		}
		if (communityJoinInfo != null) {
			model.addAttribute("communityJoinInfo", communityJoinInfo);
		}
		
		boolean oldest = "oldest".equals(sort);

		// 활동 목록은 지금 커뮤니티의 글만 보여준다.
		List<Comment> myComments = (oldest
				? commentRepository.findByAuthorOrderByCreatedAtAsc(targetUser)
				: commentRepository.findByAuthorOrderByCreatedAtDesc(targetUser)).stream()
				.filter(comment -> isPostOfCommunity(comment.getPost(), artistId))
				.toList();

		List<Post> myPosts = postService.getPostsByAuthor(targetUser, oldest).stream()
				.filter(post -> isPostOfCommunity(post, artistId))
				.toList();
		Map<Long, Long> myPostCommentCounts = new HashMap<>();
		for (Post post : myPosts) {
			myPostCommentCounts.put(post.getId(), commentService.getCommentCount(post));
		}

		List<Post> likedPosts = likeRepository.findByUserOrderByCreatedAtDesc(targetUser).stream()
				.map(Like::getPost)
				.filter(post -> isPostOfCommunity(post, artistId))
				.toList();

		// 북마크는 본인 프로필에서만 불러온다.
		List<Post> bookmarkedPosts = isOwnProfile
				? bookmarkRepository.findByUserOrderByCreatedAtDesc(targetUser).stream()
						.map(Bookmark::getPost)
						.filter(post -> isPostOfCommunity(post, artistId))
						.toList()
				: List.of();
		
		// 함께 보이는 다른 작성자 닉네임도 커뮤니티 닉네임으로 보여준다.
		List<User> profileAuthors = new ArrayList<>();
		myComments.forEach(c -> profileAuthors.add(c.getPost().getAuthor()));
		likedPosts.forEach(post -> profileAuthors.add(post.getAuthor()));
		bookmarkedPosts.forEach(post -> profileAuthors.add(post.getAuthor()));
		
		model.addAttribute("myComments", myComments);
		model.addAttribute("myPosts", myPosts);
		model.addAttribute("myPostCommentCounts", myPostCommentCounts);
		model.addAttribute("likedPosts", likedPosts);
		model.addAttribute("bookmarkedPosts", bookmarkedPosts);
		model.addAttribute("authorNicknames", communityJoinService.displayNicknamesByAuthorIdKey(profileAuthors, artistId));
		// 팔로우는 커뮤니티별이라 항상 artistId 를 함께 넘긴다.
		model.addAttribute("myFollowingCount", userFollowService.countFollowing(userId, artistId));
		model.addAttribute("followerCount", userFollowService.countFollowers(userId, artistId));
		model.addAttribute("isFollowingTarget", userFollowService.isFollowing(me, userId, artistId));
		model.addAttribute("sort", sort);
		// 프로필 카드는 항상 대상 유저 기준으로 그린다.
		model.addAttribute("targetCommunityProfile", communityJoinService.profileOf(targetUser, artistId));
		model.addAttribute("targetUser", targetUser);
		
		return "community/profile";
	}

	// 팬↔팬, 팬→아티스트 팔로우를 하나로 처리하고 Referer 로 돌아간다.
	@PostMapping("/community/{artistId}/profile/{userId}/follow")
	public String toggleFollow(
			@PathVariable Long artistId,
			@PathVariable Long userId,
			@AuthenticationPrincipal AuthenticatedUser principal,
			@RequestHeader(value = "Referer", required = false) String referer,
			HttpServletRequest request
	) {
		if (principal == null) {
			return "redirect:/login";
		}
		User me = userResolver.resolve(principal, 1L);
		try {
			userFollowService.toggle(me, userId, artistId);
		} catch (org.springframework.dao.DataIntegrityViolationException e) {
			// 중복 클릭으로 이미 팔로우된 경우 그대로 둔다.
		}
		// 우리 사이트 주소일 때만 이전 화면으로 돌아간다 (오픈 리다이렉트 방지).
		return RefererRedirects.back(referer, request, "/community/" + artistId + "/profile/" + userId);
	}

	// 팔로워·팔로잉 목록 (모달에서 fetch).
	@GetMapping("/community/{artistId}/profile/{userId}/followers")
	public String followersFragment(
			@PathVariable Long artistId,
			@PathVariable Long userId,
			@AuthenticationPrincipal AuthenticatedUser principal,
			Model model
	) {
		return userListFragment(artistId, userId, true, principal, model);
	}

	@GetMapping("/community/{artistId}/profile/{userId}/following")
	public String followingFragment(
			@PathVariable Long artistId,
			@PathVariable Long userId,
			@AuthenticationPrincipal AuthenticatedUser principal,
			Model model
	) {
		return userListFragment(artistId, userId, false, principal, model);
	}

	private String userListFragment(
			Long artistId,
			Long userId,
			boolean followers,
			AuthenticatedUser principal,
			Model model
	) {
		if (principal == null) {
			return "redirect:/login";
		}
		User me = userResolver.resolve(principal, 1L);
		User targetUser = userRepository.findOneById(userId)
				.orElseThrow(() -> new IllegalArgumentException("error.community.userNotFound"));
		if (!hasCommunityAccess(me, artistId) || !hasCommunityAccess(targetUser, artistId)) {
			return "redirect:/community/" + artistId + "/highlight";
		}

		// 상대가 콘텐츠 숨김을 켰으면 본인 외에는 목록을 보여주지 않는다.
		CommunityMember targetProfile = communityJoinService.profileOf(targetUser, artistId);
		boolean hiddenFromMe = !targetUser.getId().equals(me.getId())
				&& targetProfile != null && targetProfile.isContentHidden();
		List<User> users = hiddenFromMe ? List.of()
				: followers
				? userFollowService.listFollowers(userId, artistId)
				: userFollowService.listFollowing(userId, artistId);
		model.addAttribute("artistId", artistId);
		model.addAttribute("users", users);
		model.addAttribute("authorNicknames", communityJoinService.displayNicknamesByAuthorIdKey(users, artistId));
		return "community/fragments/userList :: userListFragment";
	}
	
	// 멤버십 가입 - 팬과 다른 커뮤니티를 방문한 아티스트 (본인 커뮤니티 제외).
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
				.orElseThrow(() -> new IllegalArgumentException("error.community.artistNotFound"));
		User member = requireMembershipEligible(principal, artistId);
		
		membershipService.join(member, artist);
		
		return "redirect:/community/" + artistId + "/highlight";
	}
	
	// 멤버십 해지.
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
				.orElseThrow(() -> new IllegalArgumentException("error.community.artistNotFound"));
		User member = requireMembershipEligible(principal, artistId);
		
		membershipService.cancel(member, artist);
		
		return "redirect:/community/" + artistId + "/highlight";
	}
	
	// 멤버십 상세 모달용 실제 가입일·만료일·연락처 (JSON).
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
	
	// 프로필 활동 목록용: 이 글이 지금 커뮤니티의 글인지.
	private static boolean isPostOfCommunity(Post post, Long artistId) {
		return post != null && post.getArtist() != null && post.getArtist().getId().equals(artistId);
	}

	private boolean hasCommunityAccess(User currentUser, Long artistId) {
		// 커뮤니티 주인 아티스트는 가입 없이 항상 열람할 수 있다.
		if (communityArtistResolver.isArtistOf(currentUser, artistId)) {
			return true;
		}
		// 관리자는 신고 처리를 위해 전체 열람한다.
		if (currentUser.getRole() == Role.ADMIN) {
			return true;
		}
		// 소속 에이전시는 바로 열람할 수 있다.
		if (currentUser.getRole() == Role.AGENCY && currentUser.agencyId() != null) {
			User artist = userRepository.findOneById(artistId).orElse(null);
			if (artist != null && currentUser.agencyId().equals(artist.agencyId())) {
				return true;
			}
		}
		return communityJoinService.isJoined(currentUser, artistId);
	}
	
	// 멤버십 가입·해지 가능 대상 확인 (본인 커뮤니티 불가).
	private User requireMembershipEligible(AuthenticatedUser principal, Long artistId) {
		User user = userResolver.resolve(principal, 1L);
		if (communityArtistResolver.isArtistOf(user, artistId)) {
			throw new IllegalStateException("error.membership.ownCommunity");
		}
		if (!user.canParticipateInCommunity()) {
			throw new IllegalStateException("error.community.fanOrArtistOnly");
		}
		return user;
	}
	
	private static boolean canSeeMembershipMedia(Model model) {
		return Boolean.TRUE.equals(model.getAttribute("isOwnCommunity"))
				|| Boolean.TRUE.equals(model.getAttribute("isManagedAgency"))
				|| Boolean.TRUE.equals(model.getAttribute("membershipActive"));
	}

	private User populateArtistModel(Long artistId, AuthenticatedUser principal, Model model) {
		User artist = userRepository.findOneById(artistId)
				.filter(user -> user.getRole() == Role.ARTIST)
				.orElseThrow(() -> new IllegalArgumentException("error.community.artistNotFound"));
		
		List<User> artistUsers = userRepository.findByRole(Role.ARTIST);
		Map<Long, String> logoUrls = portalManagementService.logoImageUrlsByArtistIds(
				artistUsers.stream().map(User::getId).toList());

		List<ArtistCardView> artists = communityUrls.withHomeUrls(artistUsers.stream()
				.map(user -> ArtistCardView.from(user, logoUrls.get(user.getId())))
				.toList());

		// homeUrl 은 커뮤니티 첫 화면 주소 (탭 링크는 뒤에 /fan 등을 붙임).
		model.addAttribute("artist", communityUrls.withHomeUrl(ArtistCardView.from(artist, logoUrls.get(artist.getId()))));
		model.addAttribute("artists", artists);
		// 포털 소개글을 커뮤니티 About 소개란에 쓴다.
		model.addAttribute("artistIntro", portalManagementService.findIntro(artist));
		model.addAttribute("artistHeaderImageUrl", portalManagementService.findHeaderImageUrl(artist));
		
		User currentUser = principal != null ? userResolver.resolve(principal, 1L) : null;
		boolean isOwnCommunity = communityArtistResolver.isArtistOf(currentUser, artist.getId());		model.addAttribute("isOwnCommunity", isOwnCommunity);
		boolean isManagedAgency = currentUser != null
				&& currentUser.getRole() == Role.AGENCY
				&& currentUser.agencyId() != null
				&& currentUser.agencyId().equals(artist.agencyId());
		model.addAttribute("isManagedAgency", isManagedAgency);
		
		// 커뮤니티 주인이 들어오면 출석을 기록한다.
		if (isOwnCommunity) {
			artistAttendanceService.recordVisitIfArtist(artist);
		}
		model.addAttribute("artistAttendance", artistAttendanceService.getAllPawColors(artist));
		Set<Long> followedIds = userFollowService.getFollowedArtistIds(currentUser);
		model.addAttribute("followingCurrentArtist", followedIds.contains(artistId));
		
		Map<Long, CommunityMember> joinedProfiles = currentUser != null
				? communityJoinService.joinedProfilesByArtistId(currentUser)
				: Collections.emptyMap();
		Set<Long> joinedArtistIds = communityJoinService.joinedArtistIds(currentUser);
		model.addAttribute("joinedArtists",
				communityDrawerHelper.joined(currentUser, artists, joinedArtistIds));
		model.addAttribute("otherCommunities",
				communityDrawerHelper.otherCommunities(currentUser, artists));
		model.addAttribute("communityJoined", isOwnCommunity || joinedArtistIds.contains(artistId));
		CommunityMember myCommunityProfile = joinedProfiles.get(artistId);
		model.addAttribute("myCommunityProfile", myCommunityProfile);
		// 아티스트 본인 프로필은 포털에 등록한 배경·사진·소개를 쓴다.
		if (isOwnCommunity) {
			model.addAttribute("artistPortalAvatarUrl", portalManagementService.findLogoImageUrl(artist));
			model.addAttribute("artistPortalBackgroundUrl", portalManagementService.findHeaderImageUrl(artist));
			model.addAttribute("artistPortalIntro", portalManagementService.findIntro(artist));
		}
		// 헤더 '나' 아이콘 사진 (아티스트는 포털 프로필, 팬은 가입 프로필).
		String myAvatarUrl = null;
		if (isOwnCommunity) {
			myAvatarUrl = portalManagementService.findLogoImageUrl(currentUser);
		} else if (myCommunityProfile != null && myCommunityProfile.getAvatarStoredName() != null
				&& !myCommunityProfile.getAvatarStoredName().isBlank()) {
			myAvatarUrl = "/uploads/" + myCommunityProfile.getAvatarStoredName();
		}
		model.addAttribute("myAvatarUrl", myAvatarUrl);
		
		if (principal != null) {
			membershipService.getMembership(currentUser, artist).ifPresent(membership -> {
				model.addAttribute("membershipActive", !membership.isExpired());
				model.addAttribute("membershipExpiresAt", membership.getExpiresAt());
			});
		}
		
		return artist;
	}
}
