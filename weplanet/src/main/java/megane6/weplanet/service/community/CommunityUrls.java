package megane6.weplanet.service.community;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.ArtistCardView;
import megane6.weplanet.domain.entity.ArtistGroup;
import megane6.weplanet.repository.artist.ArtistGroupRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** 커뮤니티 영문 주소 (예: /kiikii → 하이라이트, /kiikii/fan → 팬 게시판, 없으면 /community/{id}). */
@Component
@RequiredArgsConstructor
public class CommunityUrls {

	// 영문·숫자로 시작하고 영문·숫자·하이픈만, 100자 이하
	public static final Pattern SLUG_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9-]{0,99}$");

	// 영문 주소 뒤에 붙는 탭
	public static final Set<String> TABS = Set.of("highlight", "fan", "artist", "notice", "media", "live", "profile");

	// 사이트 최상위 주소와 겹치는 이름은 쓸 수 없다 (새 최상위 주소를 만들면 추가).
	private static final Set<String> RESERVED = Set.of(
			"admin", "api", "artists", "blocks", "board", "chat", "code", "collection", "comments", "community",
			"css", "dashboard", "dev", "error", "favicon", "fonts", "goods", "home", "images", "img", "index",
			"js", "language", "live", "login", "logout", "media", "members", "membership", "notices",
			"notifications", "oauth2", "overview", "partner", "partnership", "payment", "payments", "policy",
			"portal", "posts", "profile", "projects", "reports", "schedule", "schedules", "select-artist",
			"settings", "settlements", "shop", "signup", "site-notices", "social-login", "static", "uploads",
			"users", "verify", "webhook", "ws-chat", "find-id", "find-password", "login-wireframe",
			"signup-wireframe", "weplanet"
	);

	private final ArtistGroupRepository artistGroupRepository;

	// 영문 주소로 쓸 수 있는 값인지
	public static boolean isUsableSlug(String value) {
		return value != null
				&& SLUG_PATTERN.matcher(value).matches()
				&& !RESERVED.contains(value.toLowerCase(Locale.ROOT));
	}

	// 커뮤니티 첫 화면(하이라이트) 주소
	public String of(Long artistId) {
		return artistGroupRepository.findById(artistId)
				.map(group -> homeUrlOf(artistId, group.getNameEn()))
				.orElse(numericHomeUrl(artistId));
	}

	// 커뮤니티 탭 주소
	public String of(Long artistId, String tab) {
		return of(artistId) + "/" + tab;
	}

	// 영문 주소 → 커뮤니티 id
	public Optional<Long> findArtistId(String slug) {
		if (!isUsableSlug(slug)) {
			return Optional.empty();
		}
		return artistGroupRepository.findFirstByNameEnOrderByIdAsc(slug).map(ArtistGroup::getId);
	}

	// 카드 목록 homeUrl 을 한 번에 채운다.
	public List<ArtistCardView> withHomeUrls(List<ArtistCardView> cards) {
		if (cards.isEmpty()) {
			return cards;
		}
		Map<Long, String> homeUrls = homeUrls(cards.stream().map(ArtistCardView::id).toList());
		return cards.stream()
				.map(card -> card.withHomeUrl(homeUrls.getOrDefault(card.id(), numericHomeUrl(card.id()))))
				.toList();
	}

	public ArtistCardView withHomeUrl(ArtistCardView card) {
		return card.withHomeUrl(of(card.id()));
	}

	private Map<Long, String> homeUrls(Collection<Long> artistIds) {
		return artistGroupRepository.findAllById(artistIds).stream()
				.collect(Collectors.toMap(ArtistGroup::getId, group -> homeUrlOf(group.getId(), group.getNameEn())));
	}

	private static String homeUrlOf(Long artistId, String nameEn) {
		return isUsableSlug(nameEn)
				? "/" + nameEn.toLowerCase(Locale.ROOT)
				: numericHomeUrl(artistId);
	}

	private static String numericHomeUrl(Long artistId) {
		return "/community/" + artistId;
	}
}
