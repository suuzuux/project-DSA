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

/** 소프트 삭제 30일 후 디스크 파일과 파일 기록만 지운다 (게시물 기록은 유지). */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeletedMediaFileCleanupScheduler {
	
	private static final long KEEP_DAYS_AFTER_DELETE = 30;
	
	private final BoardMediaRepository boardMediaRepository;
	private final FileStorageService fileStorageService;
	
	// 휴면 배치(03:00)와 겹치지 않게 매일 04:00
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
			// orphanRemoval 로 파일 행도 함께 삭제된다.
			post.getFiles().clear();
		}
		if (!posts.isEmpty()) {
			log.info("[미디어 정리] 삭제 후 {}일 지난 게시물 {}건의 파일 {}개 정리", KEEP_DAYS_AFTER_DELETE, posts.size(), fileCount);
		}
	}
}
