package megane6.weplanet.service.community;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.ArtistCardView;
import megane6.weplanet.domain.entity.ArtistGroup;
import megane6.weplanet.repository.ArtistGroupRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 커뮤니티 영문 주소.
 *   localhost:9999/kiikii       → 하이라이트 (/community/17)
 *   localhost:9999/kiikii/fan   → 팬 게시판 (/community/17/fan)
 * 영문 주소 = artist_groups.name_en. 영문명이 없거나 주소로 못 쓰는 모양이면 숫자 주소(/community/{id})를 쓴다.
 *
 * 템플릿에서는 ArtistCardView.homeUrl()을 쓴다: th:href="@{${artist.homeUrl() + '/fan'}}"
 * (Thymeleaf 3.1은 링크 주소 안에서 @빈 호출을 막으므로, 주소는 컨트롤러에서 모델로 넘긴다)
 * 실제 요청 처리는 CommunitySlugForwardFilter가 영문 주소를 숫자 주소로 내부 전달(forward)해서 한다.
 */
@Component
@RequiredArgsConstructor
public class CommunityUrls {

	// 영문/숫자로 시작, 영문/숫자/하이픈만. 길이는 artist_groups.name_en(100자)에 맞춘다.
	public static final Pattern SLUG_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9-]{0,99}$");

	// 영문 주소 뒤에 붙일 수 있는 커뮤니티 탭 (/kiikii/fan)
	public static final Set<String> TABS = Set.of("highlight", "fan", "artist", "notice", "media", "live", "profile");

	// 사이트의 최상위 주소와 겹치면 그 화면을 가려버리므로 영문명으로 쓸 수 없다.
	// 컨트롤러에 새 최상위 주소(/xxx)를 만들면 여기에도 추가할 것.
	private static final Set<String> RESERVED = Set.of(
			"admin", "api", "artists", "blocks", "board", "chat", "code", "collection", "comments", "community",
			"css", "csrf-expired", "dashboard", "dev", "error", "favicon", "fonts", "goods", "home", "images", "img", "index",
			"js", "language", "live", "login", "logout", "media", "members", "membership", "notices",
			"notifications", "oauth2", "overview", "partner", "partnership", "payment", "payments", "policy",
			"portal", "posts", "profile", "projects", "reports", "schedule", "schedules", "select-artist",
			"settings", "settlements", "shop", "signup", "site-notices", "social-login", "static", "uploads",
			"users", "verify", "webhook", "ws-chat", "find-id", "find-password", "login-wireframe",
			"signup-wireframe", "weplanet"
	);

	private final ArtistGroupRepository artistGroupRepository;

	// 영문 주소로 쓸 수 있는 값인지 (모양 + 금지어)
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

	// 커뮤니티 탭 주소 (tab: fan / artist / notice / media / live / profile / highlight)
	public String of(Long artistId, String tab) {
		return of(artistId) + "/" + tab;
	}

	// 영문 주소 → 커뮤니티(그룹) id
	public Optional<Long> findArtistId(String slug) {
		if (!isUsableSlug(slug)) {
			return Optional.empty();
		}
		return artistGroupRepository.findFirstByNameEnOrderByIdAsc(slug).map(ArtistGroup::getId);
	}

	// 카드 목록(메인/햄버거 메뉴)의 homeUrl을 한 번의 조회로 채운다
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
