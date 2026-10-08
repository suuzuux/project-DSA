package megane6.weplanet.service;

import megane6.weplanet.service.main.FileStorageService;
import megane6.weplanet.service.chat.ChatFilterService;
import megane6.weplanet.service.fan.PostService;

import megane6.weplanet.domain.entity.BoardType;
import megane6.weplanet.domain.entity.Post;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.repository.fan.BookmarkRepository;
import megane6.weplanet.repository.comment.CommentReportRepository;
import megane6.weplanet.repository.comment.CommentRepository;
import megane6.weplanet.repository.fan.LikeRepository;
import megane6.weplanet.repository.fan.PostAttachmentRepository;
import megane6.weplanet.repository.fan.PostRepository;
import megane6.weplanet.repository.fan.ReportRepository;
import megane6.weplanet.service.email.CommunityActivityNotifier;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostServiceTest {

	@Test
	void communityPostsAreRequestedTenAtATime() {
		PostRepository postRepository = mock(PostRepository.class);
		PostService service = serviceWith(postRepository);
		User artist = mock(User.class);
		when(postRepository.findByBoardTypeAndArtist(
				org.mockito.ArgumentMatchers.eq(BoardType.FAN),
				org.mockito.ArgumentMatchers.same(artist),
				org.mockito.ArgumentMatchers.any(Pageable.class)))
				.thenReturn(new SliceImpl<>(List.of(), PageRequest.of(0, 10), true));

		var result = service.getCommunityPostSlice(BoardType.FAN, artist, "latest", 0, false);

		org.mockito.ArgumentCaptor<Pageable> pageable = org.mockito.ArgumentCaptor.forClass(Pageable.class);
		verify(postRepository).findByBoardTypeAndArtist(
				org.mockito.ArgumentMatchers.eq(BoardType.FAN),
				org.mockito.ArgumentMatchers.same(artist),
				pageable.capture());
		assertEquals(10, pageable.getValue().getPageSize());
		assertTrue(result.hasNext());
	}

	@Test
	void homePopularPostsExcludeArtistHiddenPosts() {
		PostRepository postRepository = mock(PostRepository.class);
		PostService service = serviceWith(postRepository);
		List<Post> visiblePosts = List.of(Post.builder().id(1L).build());
		// 메인 인기글은 아티스트 게시판(BoardType.ARTIST) 글만 대상으로 조회한다
		when(postRepository.findTop4ByBoardTypeAndHiddenFromArtistFalseAndArtistIsNotNullOrderByLikeCountDescCreatedAtDesc(BoardType.ARTIST))
				.thenReturn(visiblePosts);

		assertEquals(visiblePosts, service.getPopularPosts());
		verify(postRepository)
				.findTop4ByBoardTypeAndHiddenFromArtistFalseAndArtistIsNotNullOrderByLikeCountDescCreatedAtDesc(BoardType.ARTIST);
	}

	private PostService serviceWith(PostRepository postRepository) {
		return new PostService(
				postRepository,
				mock(LikeRepository.class),
				mock(BookmarkRepository.class),
				mock(CommentRepository.class),
				mock(CommunityActivityNotifier.class),
				mock(ReportRepository.class),
				mock(CommentReportRepository.class),
				mock(PostAttachmentRepository.class),
				mock(FileStorageService.class),
				mock(ApplicationEventPublisher.class),
				mock(ChatFilterService.class));
	}
}
