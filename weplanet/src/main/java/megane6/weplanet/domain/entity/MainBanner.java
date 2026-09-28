package megane6.weplanet.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import megane6.weplanet.domain.entity.enumfolder.BannerType;

import java.time.LocalDateTime;

/**
 * 메인 페이지(/) 상단 배너. 최고관리자가 [통합 대시보드 > 배너 영역 관리]에서 관리한다.
 * 위버스 배너처럼 "아티스트 영문명 / 대제목 / 본문 / 이미지"로 구성되고,
 * 배경색은 이미지에서 뽑은 대표색(bgColor)을 쓴다. 글자색(textColor)은 배경 밝기에 맞춰 흰색/차콜 중 하나.
 */
@Entity
@Table(name = "main_banner")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MainBanner {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(name = "banner_type", nullable = false, length = 20)
	private BannerType bannerType;

	// 홍보할 아티스트(커뮤니티 = 그룹 계정 users.id). 배너의 영문명 표시와 커뮤니티 링크에 쓴다
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "artist_id", nullable = false)
	private User artist;

	// 상품 홍보일 때만 - 클릭하면 이 상품 상세로 간다
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "goods_id")
	private Goods goods;

	@Column(nullable = false, length = 60)
	private String title;

	@Column(length = 120)
	private String body;

	@Column(name = "image_stored_name", nullable = false)
	private String imageStoredName;

	@Column(name = "bg_color", nullable = false, length = 7)
	private String bgColor;

	@Column(name = "text_color", nullable = false, length = 7)
	private String textColor;

	// 메인에 노출할지 (끄면 목록에는 남고 메인에서만 빠진다)
	@Column(nullable = false)
	private boolean active;

	// 작을수록 앞 슬라이드
	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "created_by", nullable = false)
	private User createdBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	public static MainBanner create(User createdBy) {
		MainBanner banner = new MainBanner();
		banner.createdBy = createdBy;
		return banner;
	}

	public void apply(BannerType bannerType, User artist, Goods goods, String title, String body,
					  boolean active, int sortOrder) {
		this.bannerType = bannerType;
		this.artist = artist;
		this.goods = bannerType == BannerType.PRODUCT ? goods : null;
		this.title = title;
		this.body = body;
		this.active = active;
		this.sortOrder = sortOrder;
	}

	public void changeImage(String imageStoredName) {
		this.imageStoredName = imageStoredName;
	}

	public void changeColors(String bgColor, String textColor) {
		this.bgColor = bgColor;
		this.textColor = textColor;
	}

	public void toggleActive() {
		this.active = !this.active;
	}

	@PrePersist
	public void prePersist() {
		LocalDateTime now = LocalDateTime.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	public void preUpdate() {
		this.updatedAt = LocalDateTime.now();
	}
}
