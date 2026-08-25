package megane6.weplanet.controller;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.BoardType;
import megane6.weplanet.domain.entity.Post;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.service.CommentService;
import megane6.weplanet.service.PostService;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class PostListModelHelper {

	private final PostService postService;
	private final CommentService commentService;

	public void populate(Model model, BoardType boardType, String sort) {
		populate(model, boardType, sort, null, false);
	}

	// 커뮤니티별 게시판 목록 (artist가 null이면 레거시 전역 게시판)
	public void populate(Model model, BoardType boardType, String sort, User artist) {
		populate(model, boardType, sort, artist, false);
	}

	// hideFromArtists=true면, hiddenFromArtist(Hide from Artists 토글)가 켜진 글을 목록에서 뺌
	// (36번: 아티스트 계정으로 팬 게시판을 볼 때는 숨긴 글이 안 보여야 함)
	public void populate(Model model, BoardType boardType, String sort, User artist, boolean hideFromArtists) {
		List<Post> posts = artist != null
				? postService.getPostsByBoardTypeAndArtist(boardType, artist, sort)
				: postService.getPostsByBoardType(boardType, sort);

		if (hideFromArtists) {
			posts = posts.stream().filter(post -> !post.isHiddenFromArtist()).toList();
		}

		Map<Long, Long> commentCounts = new HashMap<>();
		Map<Long, String> thumbnailUrls = new HashMap<>();
		for (Post post : posts) {
			commentCounts.put(post.getId(), commentService.getCommentCount(post));

			postService.getAttachments(post).stream()
					.filter(a -> a.isImage())
					.findFirst()
					.ifPresent(a -> thumbnailUrls.put(post.getId(), a.getStoredName()));
		}

		model.addAttribute("posts", posts);
		model.addAttribute("boardType", boardType);
		model.addAttribute("sort", sort);
		model.addAttribute("commentCounts", commentCounts);
		model.addAttribute("thumbnailUrls", thumbnailUrls);
	}
}
