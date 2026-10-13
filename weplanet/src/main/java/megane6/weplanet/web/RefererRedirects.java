package megane6.weplanet.web;

import jakarta.servlet.http.HttpServletRequest;

import java.net.URI;

/** 이전 화면 리다이렉트를 우리 사이트 안으로만 보낸다 (오픈 리다이렉트 방지). */
public final class RefererRedirects {

	private RefererRedirects() {
	}

	/** "redirect:경로" 문자열 반환 */
	public static String back(String referer, HttpServletRequest request, String fallbackPath) {
		return "redirect:" + safePath(referer, request, fallbackPath);
	}

	/** Referer 가 우리 사이트면 그 경로, 아니면 fallbackPath */
	static String safePath(String referer, HttpServletRequest request, String fallbackPath) {
		if (referer == null || referer.isBlank()) {
			return fallbackPath;
		}
		URI uri;
		try {
			uri = URI.create(referer.trim());
		} catch (IllegalArgumentException e) {
			return fallbackPath;
		}
		String scheme = uri.getScheme();
		if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
			return fallbackPath;
		}
		// 프록시 뒤에서도 실제 접속 호스트로 비교한다.
		if (uri.getHost() == null || !uri.getHost().equalsIgnoreCase(request.getServerName())) {
			return fallbackPath;
		}
		String path = uri.getRawPath();
		if (path == null || !path.startsWith("/") || path.startsWith("//")) {
			return fallbackPath;
		}
		return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
	}
}
