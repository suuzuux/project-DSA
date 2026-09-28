package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.ArtistGroup;
import megane6.weplanet.domain.entity.Goods;
import megane6.weplanet.domain.entity.MainBanner;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.BannerType;
import megane6.weplanet.domain.entity.enumfolder.GoodsStatus;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.exception.LocalizedIllegalArgumentException;
import megane6.weplanet.repository.ArtistGroupRepository;
import megane6.weplanet.repository.GoodsRepository;
import megane6.weplanet.repository.MainBannerRepository;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.service.community.CommunityUrls;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 메인 배너 관리 (최고관리자 > 통합 대시보드 > 배너 영역 관리).
 * 배너 = 아티스트 영문명 / 대제목 / 본문 / 이미지. 배경색은 이미지의 평균색을 자동으로 쓴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class MainBannerService {

	public static final int TITLE_MAX = 60;
	public static final int BODY_MAX = 120;

	private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");
	private static final String DEFAULT_BG = "#4a4a4a";
	private static final String TEXT_LIGHT = "#ffffff";
	private static final String TEXT_DARK = "#2c2c2c";

	private final MainBannerRepository mainBannerRepository;
	private final UserRepository userRepository;
	private final ArtistGroupRepository artistGroupRepository;
	private final GoodsRepository goodsRepository;
	private final FileStorageService fileStorageService;
	private final CommunityUrls communityUrls;

	// ---------- 메인 페이지 ----------

	// 노출 중인 배너 → 슬라이드. 판매 중이 아닌 상품을 가리키는 배너는 깨진 링크가 되므로 빼고 보여준다
	@Transactional(readOnly = true)
	public List<Slide> activeSlides() {
		List<MainBanner> banners = mainBannerRepository.findByActiveTrueOrderBySortOrderAscIdDesc();
		Map<Long, String> nameEns = nameEnsOf(banners);
		return banners.stream()
				.filter(banner -> linkOf(banner) != null)
				.map(banner -> new Slide(
						labelOf(banner.getArtist(), nameEns),
						banner.getTitle(),
						banner.getBody(),
						"/uploads/" + banner.getImageStoredName(),
						banner.getBgColor(),
						banner.getTextColor(),
						linkOf(banner)))
				.toList();
	}

	// ---------- 관리 화면 ----------

	@Transactional(readOnly = true)
	public List<BannerRow> listAll() {
		List<MainBanner> banners = mainBannerRepository.findAllByOrderBySortOrderAscIdDesc();
		Map<Long, String> nameEns = nameEnsOf(banners);
		return banners.stream()
				.map(banner -> {
					String link = linkOf(banner);
					return new BannerRow(
							banner.getId(),
							banner.getBannerType().getMessageKey(),
							banner.getArtist().getNickname(),
							labelOf(banner.getArtist(), nameEns),
							banner.getGoods() != null ? banner.getGoods().getName() : null,
							banner.getTitle(),
							banner.getBody(),
							"/uploads/" + banner.getImageStoredName(),
							banner.getBgColor(),
							banner.getTextColor(),
							link,
							banner.isActive(),
							banner.getSortOrder());
				})
				.toList();
	}

	@Transactional(readOnly = true)
	public MainBanner get(Long bannerId) {
		return mainBannerRepository.findById(bannerId)
				.orElseThrow(() -> new IllegalArgumentException("error.banner.notFound"));
	}

	// 폼의 아티스트 선택지 - 모든 커뮤니티(그룹 계정). 영문명이 있으면 같이 보여준다
	@Transactional(readOnly = true)
	public List<ArtistOption> artistOptions() {
		List<User> artists = userRepository.findByRole(Role.ARTIST);
		Map<Long, String> nameEns = artistGroupRepository.findAllById(artists.stream().map(User::getId).toList())
				.stream()
				.filter(group -> group.getNameEn() != null && !group.getNameEn().isBlank())
				.collect(Collectors.toMap(ArtistGroup::getId, ArtistGroup::getNameEn));
		return artists.stream()
				.map(artist -> new ArtistOption(artist.getId(), artist.getNickname(), nameEns.get(artist.getId())))
				.toList();
	}

	// 폼의 상품 선택지 - 판매 중인 굿즈만. 아티스트를 고르면 화면(JS)에서 그 아티스트 상품만 남긴다
	@Transactional(readOnly = true)
	public List<GoodsOption> goodsOptions() {
		return goodsRepository.findByStatusAndDeletedAtIsNullOrderBySortOrderAscIdAsc(GoodsStatus.ON_SALE).stream()
				.map(goods -> new GoodsOption(goods.getId(), goods.getName(), goods.getArtist().getId()))
				.toList();
	}

	// 등록(bannerId == null) / 수정. 새 이미지를 올리면 교체하고, 배경색을 비워두면 이미지에서 다시 뽑는다
	public MainBanner save(User admin, Long bannerId, BannerForm form, MultipartFile image) {
		MainBanner banner = bannerId == null ? MainBanner.create(admin) : get(bannerId);

		BannerType type = form.bannerType() == null ? BannerType.COMMUNITY : form.bannerType();
		if (form.artistId() == null) {
			throw new IllegalArgumentException("error.banner.artistRequired");
		}
		User artist = userRepository.findOneById(form.artistId())
				.filter(user -> user.getRole() == Role.ARTIST)
				.orElseThrow(() -> new IllegalArgumentException("error.banner.artistRequired"));

		Goods goods = null;
		if (type == BannerType.PRODUCT) {
			goods = form.goodsId() == null ? null : goodsRepository.findByIdAndDeletedAtIsNull(form.goodsId())
					.filter(g -> g.getStatus() == GoodsStatus.ON_SALE)
					.orElse(null);
			if (goods == null) {
				throw new IllegalArgumentException("error.banner.goodsRequired");
			}
			if (!goods.getArtist().getId().equals(artist.getId())) {
				throw new IllegalArgumentException("error.banner.goodsArtistMismatch");
			}
		}

		// 예외 메시지는 메시지 키 (컨트롤러에서 Messages.resolve(e)로 번역). 글자 수 한도는 TITLE_MAX/BODY_MAX 를 {0}으로 넘긴다
		String title = requireText(form.title(), "error.banner.titleRequired", TITLE_MAX,
				"error.banner.titleTooManyLines", "error.banner.titleTooLong");
		String body = optionalText(form.body(), BODY_MAX,
				"error.banner.bodyTooManyLines", "error.banner.bodyTooLong");

		boolean hasNewImage = image != null && !image.isEmpty();
		if (!hasNewImage && banner.getImageStoredName() == null) {
			throw new IllegalArgumentException("error.banner.imageRequired");
		}
		if (hasNewImage) {
			String contentType = image.getContentType();
			if (contentType == null || !contentType.startsWith("image/")) {
				throw new IllegalArgumentException("error.banner.imageOnly");
			}
		}

		boolean active = form.active() == null || form.active();
		int sortOrder = form.sortOrder() == null ? 0 : Math.max(0, form.sortOrder());
		banner.apply(type, artist, goods, title, body, active, sortOrder);

		String oldImage = banner.getImageStoredName();
		if (hasNewImage) {
			banner.changeImage(fileStorageService.store(image));
		}

		// 배경색: 관리자가 고른 값(화면에서 이미지로 자동 계산해 채워줌)이 있으면 그대로, 없으면 서버가 이미지에서 뽑는다
		String bgColor = isHexColor(form.bgColor()) ? form.bgColor().toLowerCase()
				: (hasNewImage || banner.getBgColor() == null)
						? averageColorOf(banner.getImageStoredName())
						: banner.getBgColor();
		banner.changeColors(bgColor, textColorFor(bgColor));

		MainBanner saved = mainBannerRepository.save(banner);
		if (hasNewImage && oldImage != null) {
			fileStorageService.delete(oldImage);
		}
		return saved;
	}

	public void toggleActive(Long bannerId) {
		get(bannerId).toggleActive();
	}

	public void delete(Long bannerId) {
		MainBanner banner = get(bannerId);
		mainBannerRepository.delete(banner);
		fileStorageService.delete(banner.getImageStoredName());
	}

	// ---------- 내부 ----------

	private String linkOf(MainBanner banner) {
		if (banner.getBannerType() == BannerType.PRODUCT) {
			Goods goods = banner.getGoods();
			if (goods == null || goods.isDeleted() || goods.getStatus() != GoodsStatus.ON_SALE) {
				return null;	// 판매 종료/삭제된 상품 - 메인에서는 빼고, 관리 목록에는 "링크 없음"으로 표시
			}
			return "/shop/products/" + goods.getId();
		}
		return communityUrls.of(banner.getArtist().getId());
	}

	private Map<Long, String> nameEnsOf(List<MainBanner> banners) {
		List<Long> artistIds = banners.stream().map(banner -> banner.getArtist().getId()).distinct().toList();
		return artistGroupRepository.findAllById(artistIds).stream()
				.filter(group -> group.getNameEn() != null && !group.getNameEn().isBlank())
				.collect(Collectors.toMap(ArtistGroup::getId, ArtistGroup::getNameEn, (a, b) -> a));
	}

	// 배너 맨 위 작은 글씨 - 아티스트 영문명(대문자). 영문명이 없으면 커뮤니티 이름
	private static String labelOf(User artist, Map<Long, String> nameEns) {
		String nameEn = nameEns.get(artist.getId());
		return nameEn != null ? nameEn.toUpperCase() : artist.getNickname();
	}

	// 이미지의 평균색. 너무 밝거나 어두운 끝 부분이 섞여도 무난한 배경이 되도록 픽셀을 듬성듬성 훑는다
	private static String averageColorOf(String storedName) {
		Path path = Paths.get("uploads").resolve(storedName);
		try (InputStream in = Files.newInputStream(path)) {
			BufferedImage img = ImageIO.read(in);
			if (img == null) {
				return DEFAULT_BG;	// ImageIO가 못 읽는 형식(webp 등)
			}
			int stepX = Math.max(1, img.getWidth() / 60);
			int stepY = Math.max(1, img.getHeight() / 60);
			long r = 0, g = 0, b = 0, n = 0;
			for (int y = 0; y < img.getHeight(); y += stepY) {
				for (int x = 0; x < img.getWidth(); x += stepX) {
					int argb = img.getRGB(x, y);
					if (((argb >>> 24) & 0xff) < 128) {
						continue;	// 투명 픽셀은 제외
					}
					r += (argb >> 16) & 0xff;
					g += (argb >> 8) & 0xff;
					b += argb & 0xff;
					n++;
				}
			}
			if (n == 0) {
				return DEFAULT_BG;
			}
			return String.format("#%02x%02x%02x", r / n, g / n, b / n);
		} catch (IOException e) {
			log.warn("배너 이미지 색상 추출 실패: {}", storedName, e);
			return DEFAULT_BG;
		}
	}

	// 배경이 밝으면 차콜 글자, 어두우면 흰 글자
	private static String textColorFor(String hex) {
		int r = Integer.parseInt(hex.substring(1, 3), 16);
		int g = Integer.parseInt(hex.substring(3, 5), 16);
		int b = Integer.parseInt(hex.substring(5, 7), 16);
		double luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
		return luminance > 0.62 ? TEXT_DARK : TEXT_LIGHT;
	}

	private static boolean isHexColor(String value) {
		return value != null && HEX_COLOR.matcher(value).matches();
	}

	private static String requireText(String value, String blankKey, int max,
									  String tooManyLinesKey, String tooLongKey) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(blankKey);
		}
		return twoLines(value, max, tooManyLinesKey, tooLongKey);
	}

	private static String optionalText(String value, int max, String tooManyLinesKey, String tooLongKey) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return twoLines(value, max, tooManyLinesKey, tooLongKey);
	}

	// 대제목/본문은 배너에서 줄바꿈 그대로 보인다(white-space: pre-line).
	// 브라우저가 보내는 \r\n 을 \n 으로 맞추고, 빈 줄은 지우고, 최대 두 줄까지만 허용한다
	private static String twoLines(String value, int max, String tooManyLinesKey, String tooLongKey) {
		List<String> lines = value.replace("\r\n", "\n").replace('\r', '\n').lines()
				.map(String::strip)
				.filter(line -> !line.isEmpty())
				.toList();
		if (lines.size() > 2) {
			throw new IllegalArgumentException(tooManyLinesKey);
		}
		String joined = String.join("\n", lines);
		if (joined.length() > max) {
			throw new LocalizedIllegalArgumentException(tooLongKey, max);
		}
		return joined;
	}

	// ---------- 화면용 값 ----------

	// 폼 입력값. @ModelAttribute로 폼 필드 이름 그대로 담긴다
	public record BannerForm(
			BannerType bannerType,
			Long artistId,
			Long goodsId,
			String title,
			String body,
			String bgColor,
			Boolean active,		// 비어 오면(null) 노출로 본다
			Integer sortOrder	// 칸을 비우면 null - 0으로 본다
	) {}

	// 메인 페이지 슬라이드 한 장
	public record Slide(String label, String title, String body, String imageUrl,
						String bgColor, String textColor, String linkUrl) {}

	// 관리 목록 한 줄. linkUrl == null 이면 판매 종료 등으로 메인에서 빠지는 배너
	// typeMessageKey: 배너 종류 메시지 키 - 화면(admin/banners.html)에서 #{${...}}로 번역한다
	public record BannerRow(Long id, String typeMessageKey, String artistName, String label, String goodsName,
							String title, String body, String imageUrl, String bgColor, String textColor,
							String linkUrl, boolean active, int sortOrder) {}

	public record ArtistOption(Long id, String name, String nameEn) {}

	public record GoodsOption(Long id, String name, Long artistId) {}
}
