package megane6.weplanet.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RefererRedirectsTest {

	private static final String FALLBACK = "/community/1/profile/2";

	private final MockHttpServletRequest request = request("weplanet.example.com");

	@Test
	void sameSiteRefererGoesBackToThatPage() {
		assertEquals("/community/1/fan?sort=latest",
				RefererRedirects.safePath("https://weplanet.example.com/community/1/fan?sort=latest", request, FALLBACK));
	}

	// 다른 사이트에서 폼을 제출하면 그 사이트로 보내지 않고 기본 주소로 (오픈 리다이렉트 방지)
	@Test
	void otherSiteRefererFallsBack() {
		assertEquals(FALLBACK, RefererRedirects.safePath("https://evil.example.org/phishing", request, FALLBACK));
	}

	@Test
	void missingOrOddRefererFallsBack() {
		assertEquals(FALLBACK, RefererRedirects.safePath(null, request, FALLBACK));
		assertEquals(FALLBACK, RefererRedirects.safePath("javascript:alert(1)", request, FALLBACK));
		assertEquals(FALLBACK, RefererRedirects.safePath("https://weplanet.example.com//evil.example.org", request, FALLBACK));
		assertEquals(FALLBACK, RefererRedirects.safePath("not a url", request, FALLBACK));
	}

	@Test
	void backBuildsRedirectViewName() {
		assertEquals("redirect:/", RefererRedirects.back("https://evil.example.org/", request, "/"));
	}

	private static MockHttpServletRequest request(String host) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setServerName(host);
		return request;
	}
}
