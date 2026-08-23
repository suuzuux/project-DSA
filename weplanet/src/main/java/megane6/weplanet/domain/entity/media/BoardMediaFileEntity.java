package megane6.weplanet.domain.entity.media;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "board_media")
public class BoardMediaFileEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "board_id", nullable = false)
	private BoardMediaEntity board;

	private String originalName;

	@Column(nullable = false)
	private String storedName;

	private String contentType;

	@Column(nullable = false, length = 20)
	private String mediaType;      // "IMAGE" / "VIDEO"

	private Long fileSize;

	@Column(nullable = false)
	private int sortOrder;         // 표시 순서 (0부터)

	@Column(nullable = false)
	private LocalDateTime createdAt;
}
