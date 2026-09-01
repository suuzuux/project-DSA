package megane6.weplanet.controller;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.Comment;
import megane6.weplanet.domain.entity.Post;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.service.CommentService;
import megane6.weplanet.service.PostService;
import megane6.weplanet.service.community.CommunityJoinService;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PostDetailModelHelper {

	private final PostService postService;
	private final CommentService commentService;
	private final CommunityJoinService communityJoinService;

	public void populate(Model model, Post post, User currentUser) {
		populate(model, post, currentUser, null);
	}

	// [닉네임 관리] artistId가 있으면(커뮤니티 게시글 상세) 작성자/댓글 작성자 닉네임을
	// 커뮤니티 가입 닉네임으로 통일해서 보여준다. artistId가 null이면(레거시 전역 게시판) 계정 닉네임 그대로.
	public void populate(Model model, Post post, User currentUser, Long artistId) {
		List<Comment> comments = commentService.getComments(post);
		List<Comment> artistComments = comments.stream()
				.filter(c -> c.getAuthor().getRole() == Role.ARTIST)
				.toList();
		List<Comment> otherComments = comments.stream()
				.filter(c -> c.getAuthor().getRole() != Role.ARTIST)
				.toList();

		List<User> authors = new ArrayList<>();
		authors.add(post.getAuthor());
		comments.forEach(c -> authors.add(c.getAuthor()));

		Map<Long, String> authorNicknames = artistId != null
				? communityJoinService.displayNicknamesByAuthorId(authors, artistId)
				: authors.stream()
						.filter(author -> author != null)
						.collect(Collectors.toMap(User::getId, User::getNickname, (a, b) -> a, HashMap::new));

		model.addAttribute("post", post);
		model.addAttribute("comments", comments);
		model.addAttribute("artistComments", artistComments);
		model.addAttribute("otherComments", otherComments);
		model.addAttribute("attachments", postService.getAttachments(post));
		model.addAttribute("bookmarked", postService.isBookmarked(post, currentUser));
		model.addAttribute("authorNicknames", authorNicknames);
	}
}
