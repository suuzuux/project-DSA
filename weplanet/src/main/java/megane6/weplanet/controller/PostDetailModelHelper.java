package megane6.weplanet.controller;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.Comment;
import megane6.weplanet.domain.entity.Post;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.service.CommentService;
import megane6.weplanet.service.PostService;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PostDetailModelHelper {

	private final PostService postService;
	private final CommentService commentService;

	public void populate(Model model, Post post, User currentUser) {
		List<Comment> comments = commentService.getComments(post);
		List<Comment> artistComments = comments.stream()
				.filter(c -> c.getAuthor().getRole() == Role.ARTIST)
				.toList();
		List<Comment> otherComments = comments.stream()
				.filter(c -> c.getAuthor().getRole() != Role.ARTIST)
				.toList();

		model.addAttribute("post", post);
		model.addAttribute("comments", comments);
		model.addAttribute("artistComments", artistComments);
		model.addAttribute("otherComments", otherComments);
		model.addAttribute("attachments", postService.getAttachments(post));
		model.addAttribute("bookmarked", postService.isBookmarked(post, currentUser));
	}
}
