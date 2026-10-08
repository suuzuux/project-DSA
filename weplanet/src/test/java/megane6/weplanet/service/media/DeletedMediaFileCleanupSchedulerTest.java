package megane6.weplanet.service.media;

import megane6.weplanet.domain.entity.media.BoardMediaEntity;
import megane6.weplanet.domain.entity.media.BoardMediaFileEntity;
import megane6.weplanet.repository.media.BoardMediaRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 삭제된 미디어 게시물 정리 (매일 04:00): 삭제 후 30일이 지난 게시물의 사진·영상 파일만 지우고, 게시물 기록은 남긴다.
// 파일 삭제는 되돌릴 수 없으니, 무엇을 지우고 무엇을 남기는지 확인한다.
class DeletedMediaFileCleanupSchedulerTest {

	private final BoardMediaRepository repository = mock(BoardMediaRepository.class);
	private final FileStorageService fileStorageService = mock(FileStorageService.class);
	private final DeletedMediaFileCleanupScheduler scheduler =
			new DeletedMediaFileCleanupScheduler(repository, fileStorageService);

	@Test
	void deletesFilesOfPostsDeletedMoreThanThirtyDaysAgo() {
		BoardMediaEntity post = BoardMediaEntity.builder()
				.id(1L).title("삭제된 글").deletedAt(LocalDateTime.now().minusDays(31)).build();
		post.getFiles().add(file(post, "photo-uuid.jpg"));
		post.getFiles().add(file(post, "video-uuid.mp4"));
		when(repository.findByDeletedAtBeforeAndFilesIsNotEmpty(any())).thenReturn(List.of(post));

		scheduler.cleanUpDeletedMediaFiles();

		// "30일 전"을 기준으로 대상을 찾는다
		ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
		verify(repository).findByDeletedAtBeforeAndFilesIsNotEmpty(cutoff.capture());
		assertTrue(Duration.between(LocalDateTime.now().minusDays(30), cutoff.getValue()).abs().toMinutes() < 1);
		// 디스크 파일을 지우고, 파일 기록도 비운다 (orphanRemoval 로 board_media_files 행 삭제)
		verify(fileStorageService).delete("photo-uuid.jpg");
		verify(fileStorageService).delete("video-uuid.mp4");
		assertTrue(post.getFiles().isEmpty());
		// 게시물 기록 자체는 남는다
		assertEquals("삭제된 글", post.getTitle());
	}

	// 정리할 게시물이 없으면 파일을 하나도 건드리지 않는다
	@Test
	void doesNothingWhenNothingToClean() {
		when(repository.findByDeletedAtBeforeAndFilesIsNotEmpty(any())).thenReturn(List.of());

		scheduler.cleanUpDeletedMediaFiles();

		verifyNoInteractions(fileStorageService);
	}

	private static BoardMediaFileEntity file(BoardMediaEntity post, String storedName) {
		return BoardMediaFileEntity.builder().board(post).storedName(storedName).build();
	}
}
