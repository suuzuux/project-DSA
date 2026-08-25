package megane6.weplanet.controller;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.ArtistCardView;
import megane6.weplanet.domain.entity.BoardType;
import megane6.weplanet.domain.entity.Post;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.repository.CommentRepository;
import megane6.weplanet.domain.entity.Comment;
import megane6.weplanet.service.CommentService;
import megane6.weplanet.service.PostService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class CommunityController {

	private final UserRepository userRepository;
	private final PostListModelHelper postListModelHelper;
	private final PostService postService;
	private final CommentService commentService;
	private final CommentRepository commentRepository;
	private final PostDetailModelHelper postDetailModelHelper;
	private final AuthenticatedUserResolver userResolver;

	@GetMapping({"/community/{artistId}", "/community/{artistId}/highlight"})
	public String highlight(@PathVariable Long artistId, Model model) {
		User artist = populateArtistModel(artistId, model);

		// "Fan Posts" 위젯 - 팬 게시판 최신 게시글 상위 4개 + 댓글 수/대표 이미지
		List<Post> fanPosts = postService.getRecentPosts(BoardType.FAN);
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

		return "community/highlight";
	}

	@GetMapping("/community/{artistId}/fan")
	public String fan(
			@PathVariable Long artistId,
			@RequestParam(defaultValue = "latest") String sort,
			@RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
			Model model
	) {
		populateArtistModel(artistId, model);
		postListModelHelper.populate(model, BoardType.FAN, sort);

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
		populateArtistModel(artistId, model);

		Post post = postService.getPost(postId);
		if (post.getBoardType() != BoardType.FAN) {
			throw new IllegalArgumentException("팬 게시글이 아닙니다.");
		}

		User currentUser = userResolver.resolve(principal, 1L);
		postDetailModelHelper.populate(model, post, currentUser);

		return "community/fan-detail";
	}

	@GetMapping("/community/{artistId}/artist")
	public String artistBoard(
			@PathVariable Long artistId,
			@RequestParam(defaultValue = "latest") String sort,
			@RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
			Model model
	) {
		populateArtistModel(artistId, model);
		postListModelHelper.populate(model, BoardType.ARTIST, sort);

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
		populateArtistModel(artistId, model);

		Post post = postService.getPost(postId);
		if (post.getBoardType() != BoardType.ARTIST) {
			throw new IllegalArgumentException("아티스트 게시글이 아닙니다.");
		}

		User currentUser = userResolver.resolve(principal, 1L);
		postDetailModelHelper.populate(model, post, currentUser);

		return "community/artist-detail";
	}

	@GetMapping("/community/{artistId}/notice")
	public String notice(@PathVariable Long artistId, Model model) {
		populateArtistModel(artistId, model);
		return "community/notice";
	}

	@GetMapping("/community/{artistId}/media")
	public String media(@PathVariable Long artistId, Model model) {
		populateArtistModel(artistId, model);
		return "community/media";
	}

	@GetMapping("/community/{artistId}/live")
	public String live(@PathVariable Long artistId, Model model) {
		populateArtistModel(artistId, model);
		return "community/live";
	}

	private User populateArtistModel(Long artistId, Model model) {
		User artist = userRepository.findById(artistId)
				.filter(user -> user.getRole() == Role.ARTIST)
				.orElseThrow(() -> new IllegalArgumentException("아티스트를 찾을 수 없습니다."));

		List<ArtistCardView> artists = userRepository.findByRole(Role.ARTIST).stream()
				.map(ArtistCardView::from)
				.toList();

		model.addAttribute("artist", ArtistCardView.from(artist));
		model.addAttribute("artists", artists);

		return artist;
	}
}
