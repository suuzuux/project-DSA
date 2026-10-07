package megane6.weplanet.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.ArtistCardView;
import megane6.weplanet.domain.dto.community.CommunityJoinInfo;
import megane6.weplanet.domain.dto.live.LiveStatusView;
import megane6.weplanet.domain.entity.*;
import megane6.weplanet.domain.entity.community.CommunityMember;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.domain.event.BadgeActivityEvent;
import megane6.weplanet.repository.BookmarkRepository;
import megane6.weplanet.repository.CommentRepository;
import megane6.weplanet.repository.LikeRepository;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.CommentService;
import megane6.weplanet.service.MembershipService;
import megane6.weplanet.service.PostService;
import megane6.weplanet.service.UserFollowService;
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
	// [머지 충돌 해결] main에서 포털(Portal) 기능이 되돌려지면서 PortalManagementService 클래스 자체가
	// 삭제됨 -> portalManagementService 필드도 함께 제거 (남기면 타입을 못 찾아 컴파일 실패)
	private final UserFollowService userFollowService; // 사람↔사람 팔로우 (팬↔팬, 팬→아티스트 공용)
	private final CommunityJoinService communityJoinService;
	private final megane6.weplanet.service.community.CommunityDrawerHelper communityDrawerHelper;
	private final ArtistAttendanceService artistAttendanceService;
	private final PortalManagementService portalManagementService;
	private final LiveBroadcastService liveBroadcastService;
	
	private final ApplicationEventPublisher eventPublisher; // [배지] 시청 알림 발행용
	// flash로 내보내는 예외 메시지(키 또는 문장)를 현재 로케일 문구로 바꾸는 데 사용
	private final megane6.weplanet.i18n.Messages messages;
	private final megane6.weplanet.service.ContentTranslationService contentTranslationService; // 공지 번역보기 (AI)
	
	private final CommunityArtistResolver communityArtistResolver;

	private final CommunityUrls communityUrls; // 탭/메뉴 링크를 영문 주소(/kiikii/fan)로

	@GetMapping({"/community/{artistId}", "/community/{artistId}/highlight"})
	public String highlight(@PathVariable Long artistId, @AuthenticationPrincipal AuthenticatedUser principal, Model model) {
		User artist = populateArtistModel(artistId, principal, model);
		
		// "Fan Posts" 위젯 - 이 커뮤니티 팬 게시판 최신 게시글 상위 6개 + 댓글 수/대표 이미지
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
		// [닉네임 관리] Fan Posts 위젯도 작성자가 팬이라 가입할 때 정한 커뮤니티 닉네임으로 통일해서 보여준다
		model.addAttribute("fanPostAuthorNicknames", communityJoinService.displayNicknamesByAuthorIdKey(
				fanPosts.stream().map(Post::getAuthor).toList(), artistId));
		
		// "Comments by 아티스트" 위젯 - 그룹 계정 + 멤버 계정이 이 커뮤니티에 쓴 댓글·답글 최신 6개
		List<Comment> artistComments = commentRepository
				.findTop6ByAuthor_IdInAndPost_Artist_IdAndDeletedAtIsNullOrderByCreatedAtDesc(
						communityArtistResolver.artistSideUserIds(artistId), artistId);
		model.addAttribute("artistCommentsWidget", artistComments);
		
		// "From 아티스트" 위젯 - 이 커뮤니티 아티스트 게시판 최신 게시글 상위 6개 + 대표 이미지
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

		// "Lives" 위젯 - 한 줄 최대 3칸. 방송 중이면 LIVE 카드가 첫 칸, 나머지는 영상이 있는 라이브 다시보기 최신순
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
		// 36번: 아티스트로 로그인한 사람이 팬 게시판을 볼 땐 "Hide from Artists" 글을 목록에서 뺌
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
			@RequestParam(required = false) String filter, // "media" 면 사진/미디어 첨부 글만

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
		// 화면의 "전체 / 사진·미디어" 칩 선택 표시 + 정렬·더보기 주소에 필터 유지용
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
			throw new IllegalArgumentException("error.post.boardMismatch");
		}
		if (post.getArtist() == null || !post.getArtist().getId().equals(artistId)) {
			throw new IllegalArgumentException("error.post.notInCommunity");
		}
		
		// 36번(Hide from Artists) 필터가 목록에만 있고 상세엔 빠져 있어서,
		// 아티스트가 주소창에 /community/1/fan/5 를 직접 치면 숨긴 글이 그대로 열렸음.
		// 가입자 차단이 목록에만 있던 것과 똑같은 종류의 누락. 목록과 같은 기준을 상세에도 적용함
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

	// 커뮤니티 공지 번역보기 - 상세 화면과 같은 권한(로그인 + 이 커뮤니티 열람 가능)으로, 기본 서비스 언어로 AI 번역한다
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
		
		// [배지] 미디어 상세를 열었으면 시청으로 본다 (멤버십 게이트를 통과한 뒤 위치)
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

		// [배지] 방송 중일 때 들어온 경우만 "라이브 시청", 꺼져 있는 방에 들어온 건 시청 X
		if (liveStatus.live()) {
			eventPublisher.publishEvent(new BadgeActivityEvent(
					userResolver.resolve(principal, 1L).getId(), artistId,
					BadgeActivityEvent.Activity.LIVE_VIEWED
			));
		}

		return "community/live";
	}
	
	// "내 프로필" 버튼
	// 영문 주소 (/kiikii/profile)로 돌아오면 필터가 forward만 하므로, 주소창도 그대로 남음
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

	// 와이어프레임 20~23번: 프로필 - 댓글/포스트/좋아요/북마크 히스토리. 본인이면 편집 가능, 타인이면 그 사람 기준 + 팔로우 버튼.
	// 화면에 뜨는 이름은 계정 아이디가 아니라 커뮤니티 닉네임(populateArtistModel이 넣은 myCommunityProfile)을 쓴다.
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

		// 프로필 열람 = 나도 이 커뮤니티 가입 + 상대도 이 커뮤니티 가입.
		// hasCommunityAccess를 양쪽에 재사용해서, 아티스트 본인 프로필은 가입 여부와 무관하게 항상 열람 가능하다.
		if (!hasCommunityAccess(me, artistId) || !hasCommunityAccess(targetUser, artistId)) {
			model.addAttribute("gatedTab", "profile");
			return "community/membership-required";
		}

		boolean isOwnProfile = me.getId().equals(userId);
		model.addAttribute("isOwnProfile", isOwnProfile);
		model.addAttribute("profileUserId", userId);
		// 이 프로필의 주인이 이 커뮤니티의 아티스트 본인인지 - 맞다면 팔로우 버튼이
		// "아티스트 팔로우" 기준으로 동작한다 (UserFollowService.toggle이 알아서 가입 요건 없이 처리).
		model.addAttribute("isCommunityOwnerProfile", userId.equals(artistId));
		// 이 프로필의 주인이 이 커뮤니티의 아티스트 쪽 계정(솔로 본인/그룹 멤버)인지.
		// 맞다면 프로필 카드와 편집 패널은 가입 프로필 대신 그 계정의 포털 프로필(소개/사진/배경)로 그린다.
		boolean targetIsArtistHere = communityArtistResolver.isArtistOf(targetUser, artistId);
		model.addAttribute("isTargetArtistSide", targetIsArtistHere);
		if (targetIsArtistHere) {
			model.addAttribute("targetArtistAvatarUrl", portalManagementService.findLogoImageUrl(targetUser));
			model.addAttribute("targetArtistBackgroundUrl", portalManagementService.findHeaderImageUrl(targetUser));
			model.addAttribute("targetArtistIntro", portalManagementService.findIntro(targetUser));
		}

		// 커뮤니티 가입 당일을 D+1로 계산한다. 대상 유저 기준.
		// 아티스트 쪽 계정은 자기 커뮤니티에 따로 가입하지 않으므로 가입 행이 없을 때 계정 생성일을
		// 커뮤니티 활동 시작일로 사용한다. 팬은 기존처럼 community_members.joined_at만 사용한다.
		CommunityJoinInfo communityJoinInfo = communityJoinService.joinInfoOf(targetUser, artistId);
		if (communityJoinInfo == null && targetIsArtistHere && targetUser.getCreatedAt() != null) {
			communityJoinInfo = CommunityJoinInfo.from(targetUser.getCreatedAt(), LocalDate.now());
		}
		if (communityJoinInfo != null) {
			model.addAttribute("communityJoinInfo", communityJoinInfo);
		}
		
		boolean oldest = "oldest".equals(sort);

		// 활동 목록(댓글/포스트/좋아요/북마크)은 지금 보고 있는 커뮤니티(artistId)의 글만 보여준다 (다른 커뮤니티 글 노출 방지).
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

		// 북마크는 본인만 보는 정보라 내 프로필에서만 불러온다 (화면에서도 북마크 탭은 내 프로필에서만 보인다)
		List<Post> bookmarkedPosts = isOwnProfile
				? bookmarkRepository.findByUserOrderByCreatedAtDesc(targetUser).stream()
						.map(Bookmark::getPost)
						.filter(post -> isPostOfCommunity(post, artistId))
						.toList()
				: List.of();
		
		// [닉네임 관리] 프로필에서 댓글/좋아요/북마크한 "다른 사람들"의 글이 함께 보이는데,
		// 그 작성자 닉네임도 이 커뮤니티에서 통용되는 닉네임(가입할 때 닉네임)으로 통일해서 보여준다.
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
		// 팔로우는 이 커뮤니티(artistId)에 종속되므로 항상 artistId를 함께 넘긴다.
		// 대상이 아티스트 본인이면 이 값들이 곧 "아티스트 팔로우" 여부/카운트가 된다(따로 attribute 안 나눔).
		model.addAttribute("myFollowingCount", userFollowService.countFollowing(userId, artistId));
		model.addAttribute("followerCount", userFollowService.countFollowers(userId, artistId));
		model.addAttribute("isFollowingTarget", userFollowService.isFollowing(me, userId, artistId));
		model.addAttribute("sort", sort);
		// 프로필 카드(닉네임/소개/아바타/배경/숨김여부)는 항상 "대상 유저" 기준으로 그린다.
		// 본인 프로필이면 populateArtistModel이 넣은 myCommunityProfile과 값이 같다.
		model.addAttribute("targetCommunityProfile", communityJoinService.profileOf(targetUser, artistId));
		model.addAttribute("targetUser", targetUser);
		
		return "community/profile";
	}

	// 팔로우 버튼 하나로 팬↔팬, 팬→아티스트를 모두 처리한다 (userId == artistId면 아티스트 팔로우 - UserFollowService.toggle 판단).
	// 누른 곳(About 위젯/프로필 화면)마다 돌아갈 곳이 달라 Referer로 되돌려 보낸다.
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
			// 팔로우 버튼을 빠르게 두 번 눌러 같은 팔로우가 동시에 저장된 경우 - 이미 팔로우된 상태이므로 그대로 둔다
		}
		// 우리 사이트 주소일 때만 누른 화면으로 돌아간다 (RefererRedirects - 오픈 리다이렉트 방지)
		return RefererRedirects.back(referer, request, "/community/" + artistId + "/profile/" + userId);
	}

	// 팔로워/팔로잉 숫자 클릭 시 뜨는 리스트(닉네임+아바타) - 모달에서 fetch로 불러 씀
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

		// 상대가 이 커뮤니티에서 콘텐츠 숨김을 켰으면 본인이 아닌 사람에게는 목록을 보여주지 않는다 (주소를 직접 불러도 마찬가지).
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
	
	// Membership 가입하기 버튼 - 팬 + 타 커뮤니티 방문 아티스트 (본인 커뮤니티 제외)
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
				.orElseThrow(() -> new IllegalArgumentException("error.community.artistNotFound"));
		User member = requireMembershipEligible(principal, artistId);
		
		membershipService.cancel(member, artist);
		
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
	
	// Fan/Artist/Media/Live/Notice 탭 접근 제어: 로그인은 각 라우트에서 먼저 체크하고,
	// 여기서는 "이 커뮤니티에 가입(CommunityMember)했는지"만 확인함 (Follow는 팔로우 버튼 전용이라 가입 기준이 아님).
	// 주의: 멤버십(유료, DM 전용)과는 별개 개념 - 헷갈려서 처음엔 membershipActive로 잘못 체크했었음
	// 프로필 활동 목록용: 이 글이 지금 보고 있는 커뮤니티(artistId)의 글인지
	private static boolean isPostOfCommunity(Post post, Long artistId) {
		return post != null && post.getArtist() != null && post.getArtist().getId().equals(artistId);
	}

	private boolean hasCommunityAccess(User currentUser, Long artistId) {
		// 커뮤니티 주인(그 아티스트 본인)은 가입 절차 없이 항상 열람 가능해야 함.
		// 아티스트는 팬 전용 가입 절차를 밟을 수 없어서, 가입 여부만 보면
		// 정작 본인이 자기 게시판에서 차단당하는 문제가 있었음
		if (communityArtistResolver.isArtistOf(currentUser, artistId)) {
			return true;
		}
		// 관리자는 신고 처리 등을 위해 전체 열람이 필요함
		if (currentUser.getRole() == Role.ADMIN) {
			return true;
		}
		// 소속 에이전시는 자동 가입과 별도로 즉시 접근 가능
		if (currentUser.getRole() == Role.AGENCY && currentUser.agencyId() != null) {
			User artist = userRepository.findOneById(artistId).orElse(null);
			if (artist != null && currentUser.agencyId().equals(artist.agencyId())) {
				return true;
			}
		}
		return communityJoinService.isJoined(currentUser, artistId);
	}
	
	// 멤버십 가입/해지: 팬 + 타 커뮤니티 방문 아티스트. 본인 커뮤니티는 불가.
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

		// artist.homeUrl() = 이 커뮤니티 첫 화면 주소. 탭 링크는 이 뒤에 /fan, /artist ... 를 붙인다
		model.addAttribute("artist", communityUrls.withHomeUrl(ArtistCardView.from(artist, logoUrls.get(artist.getId()))));
		model.addAttribute("artists", artists);
		// 포털 프로필 관리의 소개글(artist_profile.intro) → 커뮤니티 About 소개란
		model.addAttribute("artistIntro", portalManagementService.findIntro(artist));
		model.addAttribute("artistHeaderImageUrl", portalManagementService.findHeaderImageUrl(artist));
		
		User currentUser = principal != null ? userResolver.resolve(principal, 1L) : null;
		boolean isOwnCommunity = communityArtistResolver.isArtistOf(currentUser, artist.getId());		model.addAttribute("isOwnCommunity", isOwnCommunity);
		boolean isManagedAgency = currentUser != null
				&& currentUser.getRole() == Role.AGENCY
				&& currentUser.agencyId() != null
				&& currentUser.agencyId().equals(artist.agencyId());
		model.addAttribute("isManagedAgency", isManagedAgency);
		
		// 커뮤니티 주인(솔로 본인 또는 그 그룹의 멤버)이 들어오면 그 커뮤니티(아티스트)의 출석을 찍는다
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
		// 아티스트 본인 '나' 프로필: 에이전시/포털에서 등록한 배경·사진·소개 반영
		if (isOwnCommunity) {
			model.addAttribute("artistPortalAvatarUrl", portalManagementService.findLogoImageUrl(artist));
			model.addAttribute("artistPortalBackgroundUrl", portalManagementService.findHeaderImageUrl(artist));
			model.addAttribute("artistPortalIntro", portalManagementService.findIntro(artist));
		}
		// 헤더 오른쪽 '나' 아이콘에 보여줄 내 프로필 사진.
		// 이 커뮤니티의 아티스트(솔로 본인/그룹 멤버)는 내 계정의 포털 프로필 사진, 팬은 이 커뮤니티 가입 프로필 사진.
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
