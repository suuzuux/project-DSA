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

/** 커뮤니티 영문 주소를 숫자 주소로 forward 한다 (등록된 영문명만, 주소창은 유지). */
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
			// 쿼리스트링은 forward 후에도 유지된다.
			request.getRequestDispatcher(target.get()).forward(request, response);
			return;
		}
		filterChain.doFilter(request, response);
	}

	// 영문 주소면 전달할 숫자 주소, 아니면 empty (SecurityConfig 공개 판단에도 사용).
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
