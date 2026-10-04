package megane6.weplanet.web;

import jakarta.servlet.http.HttpServletRequest;

import java.net.URI;

/**
 * "누른 화면으로 돌아가기" 리다이렉트를 우리 사이트 안으로만 보낸다.
 * Referer 가 같은 호스트 주소일 때만 그 경로로, 아니면 기본 주소로 보낸다 (오픈 리다이렉트 방지).
 */
public final class RefererRedirects {

	private RefererRedirects() {
	}

	/** 컨트롤러에서 바로 반환할 수 있는 "redirect:경로" 문자열 */
	public static String back(String referer, HttpServletRequest request, String fallbackPath) {
		return "redirect:" + safePath(referer, request, fallbackPath);
	}

	/** Referer 가 우리 사이트 주소면 그 경로(+쿼리), 아니면 fallbackPath */
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
		// 터널(리버스 프록시) 뒤에서도 forward-headers-strategy 덕분에 getServerName() 이 실제 접속 주소의 호스트다
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
