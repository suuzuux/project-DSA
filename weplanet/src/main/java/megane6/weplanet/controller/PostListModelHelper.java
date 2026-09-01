package megane6.weplanet.controller;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.BoardType;
import megane6.weplanet.domain.entity.Post;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.service.CommentService;
import megane6.weplanet.service.PostService;
import megane6.weplanet.service.community.CommunityJoinService;
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
	private final CommunityJoinService communityJoinService;

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

		// [닉네임 관리] 목록에 작성자 닉네임을 뿌릴 때, 커뮤니티(artist)별 게시판이면 가입할 때 설정한
		// 커뮤니티 닉네임을 쓰고, artist가 없는 레거시 전역 게시판이면 계정 닉네임을 그대로 쓴다.
		List<User> authors = posts.stream().map(Post::getAuthor).toList();
		Map<Long, String> authorNicknames = artist != null
				? communityJoinService.displayNicknamesByAuthorId(authors, artist.getId())
				: authors.stream()
						.filter(author -> author != null)
						.collect(java.util.stream.Collectors.toMap(User::getId, User::getNickname, (a, b) -> a));

		model.addAttribute("posts", posts);
		model.addAttribute("boardType", boardType);
		model.addAttribute("sort", sort);
		model.addAttribute("commentCounts", commentCounts);
		model.addAttribute("thumbnailUrls", thumbnailUrls);
		model.addAttribute("authorNicknames", authorNicknames);
	}
}
