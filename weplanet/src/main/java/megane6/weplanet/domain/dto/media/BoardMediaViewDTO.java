package megane6.weplanet.domain.dto.media;

import lombok.*;
import megane6.weplanet.domain.entity.media.BoardMediaEntity;
import megane6.weplanet.domain.entity.media.BoardMediaFileEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor(force = true)
public class BoardMediaViewDTO {
	private final Long id;
	private final Long groupId;
	private final Long uploaderId;
	private final String title;
	private final String content;
	private final LocalDateTime createdAt;
	private final List<FileView> files;

	private BoardMediaViewDTO(BoardMediaEntity e) {
		this.id = e.getId();
		this.groupId = e.getGroupId();
		this.uploaderId = e.getUploaderId();
		this.title = e.getTitle();
		this.content = e.getContent();
		this.createdAt = e.getCreatedAt();
		this.files = e.getFiles().stream().map(FileView::new).collect(Collectors.toList());
	}

	/**
	 * 엔티티 → 뷰 DTO (트랜잭션 안에서 호출)
	 */
	public static BoardMediaViewDTO from(BoardMediaEntity e) {
		return new BoardMediaViewDTO(e);
	}

	/**
	 * 대표(썸네일)로 쓸 첫 번째 파일
	 */
	public FileView getThumbnail() {
		return files.isEmpty() ? null : files.get(0);
	}

	public int getFileCount() {
		return files.size();
	}

	/**
	 * 첨부파일 한 개의 표시 정보
	 */
	@Getter
	public static class FileView {
		private final Long id;
		private final String originalName;
		private final String mediaType;   // IMAGE / VIDEO
		private final int sortOrder;

		FileView(BoardMediaFileEntity f) {
			this.id = f.getId();
			this.originalName = f.getOriginalName();
			this.mediaType = f.getMediaType();
			this.sortOrder = f.getSortOrder();
		}

	}
}
