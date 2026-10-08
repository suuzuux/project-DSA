package megane6.weplanet.controller;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.community.CommunityAuthorView;
import megane6.weplanet.domain.entity.Comment;
import megane6.weplanet.domain.entity.Post;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.repository.CommentReportRepository;
import megane6.weplanet.repository.LikeRepository;
import megane6.weplanet.service.CommentService;
import megane6.weplanet.service.PostService;
import megane6.weplanet.service.community.CommunityJoinService;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PostDetailModelHelper {

	private final PostService postService;
	private final CommentService commentService;
	private final CommunityJoinService communityJoinService;
	private final CommentReportRepository commentReportRepository;
	private final LikeRepository likeRepository;

	public void populate(Model model, Post post, User currentUser) {
		populate(model, post, currentUser, null);
	}

	// 커뮤니티 글이면 작성자 닉네임을 가입 닉네임으로 보여준다 (artistId 가 없으면 계정 닉네임).
	public void populate(Model model, Post post, User currentUser, Long artistId) {
		List<Comment> comments = commentService.getComments(post);

		// 원댓글만 목록에 두고 답글은 부모 id(문자열 키)별로 묶는다.
		Map<String, List<Comment>> repliesByParentId = comments.stream()
				.filter(c -> c.getParent() != null)
				.collect(Collectors.groupingBy(
						c -> String.valueOf(c.getParent().getId()),
						HashMap::new,
						Collectors.toList()));
		// 답글도 최신순(새 답글이 맨 위)
		repliesByParentId.values().forEach(Collections::reverse);

		// 원댓글은 최신순 (조회 결과를 뒤집음).
		List<Comment> rootComments = new ArrayList<>(comments.stream()
				.filter(c -> c.getParent() == null)
				.toList());
		Collections.reverse(rootComments);
		List<Comment> artistComments = rootComments.stream()
				.filter(c -> c.getAuthor().isArtistSide())
				.toList();
		List<Comment> otherComments = rootComments.stream()
				.filter(c -> !c.getAuthor().isArtistSide())
				.toList();

		List<User> authors = new ArrayList<>();
		authors.add(post.getAuthor());
		comments.forEach(c -> authors.add(c.getAuthor()));

		Map<String, CommunityAuthorView> authorViews = artistId != null
				? communityJoinService.authorViewsByAuthorIdKey(authors, artistId)
				: authors.stream()
						.filter(author -> author != null)
						.collect(Collectors.toMap(
								author -> String.valueOf(author.getId()),
								author -> new CommunityAuthorView(author.getNickname(), null),
								(a, b) -> a,
								HashMap::new));
		Map<String, String> authorNicknames = authorViews.entrySet().stream()
				.collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().nickname()));
		Map<String, String> authorAvatarUrls = authorViews.entrySet().stream()
				.filter(entry -> entry.getValue().avatarUrl() != null)
				.collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().avatarUrl()));

		boolean liked = currentUser != null
				&& likeRepository.findByPostAndUser(post, currentUser).isPresent();

		Set<Long> reportedCommentIds = Collections.emptySet();
		if (currentUser != null && !comments.isEmpty()) {
			List<Long> commentIds = comments.stream().map(Comment::getId).toList();
			reportedCommentIds = new HashSet<>(commentReportRepository.findReportedCommentIds(currentUser, commentIds));
		}

		model.addAttribute("post", post);
		model.addAttribute("comments", comments);
		model.addAttribute("artistComments", artistComments);
		model.addAttribute("otherComments", otherComments);
		model.addAttribute("repliesByParentId", repliesByParentId);
		model.addAttribute("attachments", postService.getAttachments(post));
		model.addAttribute("bookmarked", postService.isBookmarked(post, currentUser));
		model.addAttribute("liked", liked);
		model.addAttribute("authorNicknames", authorNicknames);
		model.addAttribute("authorAvatarUrls", authorAvatarUrls);
		model.addAttribute("reportedCommentIds", reportedCommentIds);
	}
}
