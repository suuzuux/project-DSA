package megane6.weplanet.domain.entity.media;


import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/*
* 미디어 게시글 (1). 첨부파일(board_media_files)과 1:N. - group_id → artist_groups.id (커뮤니티) -
* uploader_id → users.id (소속사) - deleted_at 소프트 삭제(값이 있으면 삭제된 글)
* */

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "board_media")
public class BoardMediaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long groupId;

	@Column(nullable = false)
	private Long uploaderId;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(length = 2000)
	private String content;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	@Column(nullable = false)
	private LocalDateTime updatedAt;

	private LocalDateTime deletedAt;

	// 첨부파일들. 게시글 저장/삭제 시 함께 처리(cascade), 목록에서 제거 시 행 삭제(orphanRemoval)
	@OneToMany(mappedBy = "board", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("sortOrder asc")
	private List<BoardMediaFileEntity> files = new ArrayList<>();

	/** 양방향 연관관계를 안전하게 설정하는 헬퍼 */
	public void addFile(BoardMediaFileEntity file) {
		files.add(file);
		file.setBoard(this);
	}
}
