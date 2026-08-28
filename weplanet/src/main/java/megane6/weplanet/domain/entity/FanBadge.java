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
	
	// 배지를 구분하는 고정 문자열. Ownership 과 이어주는 열쇠라 값이 바뀌면 안 된다.
	@Column(name = "badge_code", nullable = false, unique = true, length = 50)
	private String badgeCode;
	
	@Column(name = "badge_name", nullable = false, length = 100)
	private String badgeName;
	
	@Enumerated(EnumType.STRING)
	@Column(name = "badge_type", nullable = false, length = 20)
	private FanBadgeType badgeType;
	
	// 표시용 이모지. image_url 이 없을 때 대신 보여줌
	@Column(nullable = false, length = 8)
	private String icon;
	
	// 배지 이미지 경로 (이미지 안 만든 배지 = null, icon 사용)
	@Column(name = "image_url", length = 255)
	private String imageUrl;
	
	// 획득 조건 안내 (미획득(흑백)에는 "어떻게 얻는지"보여줄 때)
	@Column(length = 200)
	private String description;
	
	@Column(name = "sort_order", nullable = false)
	private int sortOrder;
	
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
	
}
