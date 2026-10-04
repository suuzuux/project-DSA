package megane6.weplanet.service.media;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.media.BoardMediaEntity;
import megane6.weplanet.domain.entity.media.BoardMediaFileEntity;
import megane6.weplanet.repository.media.BoardMediaRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 삭제된 미디어 게시물의 업로드 파일 정리 - 삭제(소프트 삭제) 후 30일이 지나면
 * 디스크 파일과 파일 기록(board_media_files)만 지운다. 게시물 기록(board_media)은 남는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeletedMediaFileCleanupScheduler {
	
	private static final long KEEP_DAYS_AFTER_DELETE = 30;
	
	private final BoardMediaRepository boardMediaRepository;
	private final FileStorageService fileStorageService;
	
	// 휴면계정 배치(03:00)와 겹치지 않게 새벽 4시에 하루 한 번
	@Scheduled(cron = "0 0 4 * * *")
	@Transactional
	public void cleanUpDeletedMediaFiles() {
		LocalDateTime cutoff = LocalDateTime.now().minusDays(KEEP_DAYS_AFTER_DELETE);
		List<BoardMediaEntity> posts = boardMediaRepository.findByDeletedAtBeforeAndFilesIsNotEmpty(cutoff);
		int fileCount = 0;
		for (BoardMediaEntity post : posts) {
			for (BoardMediaFileEntity file : post.getFiles()) {
				fileStorageService.delete(file.getStoredName());
				fileCount++;
			}
			// orphanRemoval = true 라서 목록을 비우면 board_media_files 행도 함께 지워진다
			post.getFiles().clear();
		}
		if (!posts.isEmpty()) {
			log.info("[미디어 정리] 삭제 후 {}일 지난 게시물 {}건의 파일 {}개 정리", KEEP_DAYS_AFTER_DELETE, posts.size(), fileCount);
		}
	}
}
