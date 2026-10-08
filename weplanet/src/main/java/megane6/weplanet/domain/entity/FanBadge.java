package megane6.weplanet.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import megane6.weplanet.domain.entity.enumfolder.FanBadgeType;

import java.time.LocalDateTime;

@Entity
@Table(name = "fan_badge")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FanBadge {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	
	// 배지 고유 코드 (Ownership 연결 키라 변경 금지)
	@Column(name = "badge_code", nullable = false, unique = true, length = 50)
	private String badgeCode;
	
	@Column(name = "badge_name", nullable = false, length = 100)
	private String badgeName;
	
	@Enumerated(EnumType.STRING)
	@Column(name = "badge_type", nullable = false, length = 20)
	private FanBadgeType badgeType;
	
	// 표시용 이모지 (이미지가 없을 때)
	@Column(nullable = false, length = 8)
	private String icon;
	
	// 배지 이미지 파일명 (없으면 null, color/grayscale 폴더에서 같은 이름 사용).
	@Column(name = "image_url", length = 255)
	private String imageUrl;
	
	// 획득 조건 안내
	@Column(length = 200)
	private String description;
	
	@Column(name = "sort_order", nullable = false)
	private int sortOrder;
	
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
	
}
