package megane6.weplanet.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import megane6.weplanet.service.community.CommunityUrls;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 커뮤니티 영문 주소를 기존 숫자 주소 화면으로 내부 전달(forward)한다. 주소창은 영문 주소 그대로 남는다.
 *   GET /kiikii      → /community/17
 *   GET /kiikii/fan  → /community/17/fan
 *
 * 컨트롤러(@GetMapping("/{slug}"))로 만들면 뷰 컨트롤러(/signup-wireframe 등)보다 먼저 잡아버려서
 * 필터로 처리한다. 영문명이 실제 커뮤니티로 등록돼 있을 때만 가로채고, 아니면 원래대로 흘려보낸다.
 * 스프링 시큐리티 필터 뒤에서 돌기 때문에(기본 순서) 로그인 검사는 원래 요청(/kiikii) 기준으로 이미 끝나 있다.
 */
@Component
@RequiredArgsConstructor
public class CommunitySlugForwardFilter extends OncePerRequestFilter {

	private static final Pattern SLUG_PATH =
			Pattern.compile("^/([A-Za-z0-9][A-Za-z0-9-]*)(?:/(highlight|fan|artist|notice|media|live|profile))?/?$");

	private final CommunityUrls communityUrls;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
									FilterChain filterChain) throws ServletException, IOException {
		Optional<String> target = forwardTargetOf(request);
		if (target.isPresent()) {
			// 쿼리스트링(?sort=popular 등)은 forward 후에도 그대로 읽힌다
			request.getRequestDispatcher(target.get()).forward(request, response);
			return;
		}
		filterChain.doFilter(request, response);
	}

	// 커뮤니티 영문 주소 요청이면 전달할 숫자 주소(/community/17/fan), 아니면 empty.
	// SecurityConfig도 이걸로 "영문 주소는 /community/** 처럼 로그인 없이 공개"를 판단한다.
	public Optional<String> forwardTargetOf(HttpServletRequest request) {
		String method = request.getMethod();
		if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
			return Optional.empty();
		}
		String path = request.getRequestURI().substring(request.getContextPath().length());
		Matcher matcher = SLUG_PATH.matcher(path);
		if (!matcher.matches() || !CommunityUrls.isUsableSlug(matcher.group(1))) {
			return Optional.empty();
		}
		String tab = matcher.group(2);
		return communityUrls.findArtistId(matcher.group(1))
				.map(artistId -> "/community/" + artistId + (tab != null ? "/" + tab : ""));
	}
}
