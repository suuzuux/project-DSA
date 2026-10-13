package megane6.weplanet.web;

import megane6.weplanet.service.community.CommunityUrls;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommunitySlugForwardFilterTest {
    private final CommunityUrls urls = mock(CommunityUrls.class);
    private final CommunitySlugForwardFilter filter = new CommunitySlugForwardFilter(urls);

    @ParameterizedTest
    @ValueSource(strings = {"project", "project/42", "project/42/modal", "project/eligibility"})
    void registeredCommunityProjectGetKeepsSlugAndForwardsToExistingRoute(String path) throws Exception {
        when(urls.findArtistId("kiikii")).thenReturn(Optional.of(1101L));
        var request = new MockHttpServletRequest("GET", "/kiikii/" + path);
        request.setQueryString("sort=latest");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertEquals("/community/1101/" + path, response.getForwardedUrl());
        assertNull(response.getRedirectedUrl());
        assertEquals("sort=latest", request.getQueryString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"project", "project/email-verification", "project/email-verification/confirm",
            "project/42/contributions", "project/42/approve", "project/42/reject"})
    void projectPostRoutesUseSameHandlers(String path) {
        when(urls.findArtistId("kiikii")).thenReturn(Optional.of(1101L));
        assertEquals(Optional.of("/community/1101/" + path),
                filter.forwardTargetOf(new MockHttpServletRequest("POST", "/kiikii/" + path)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"fan", "project/42/modal", "project/eligibility", "project/unknown", "profile"})
    void otherPostPathsAreNotAliased(String path) {
        assertTrue(filter.forwardTargetOf(new MockHttpServletRequest("POST", "/kiikii/" + path)).isEmpty());
    }

    @Test
    void unknownReservedAndNumericPathsKeepExistingBehavior() {
        when(urls.findArtistId("unknown")).thenReturn(Optional.empty());
        assertTrue(filter.forwardTargetOf(new MockHttpServletRequest("GET", "/unknown/project")).isEmpty());
        assertTrue(filter.forwardTargetOf(new MockHttpServletRequest("GET", "/admin/project")).isEmpty());
        assertTrue(filter.forwardTargetOf(new MockHttpServletRequest("GET", "/community/1101/project")).isEmpty());
        when(urls.findArtistId("kiikii")).thenReturn(Optional.of(1101L));
        assertEquals(Optional.of("/community/1101/fan"),
                filter.forwardTargetOf(new MockHttpServletRequest("GET", "/kiikii/fan")));
        var head = new MockHttpServletRequest("HEAD", "/app/kiikii/project/42/");
        head.setContextPath("/app");
        assertEquals(Optional.of("/community/1101/project/42"), filter.forwardTargetOf(head));
        assertTrue(filter.forwardTargetOf(new MockHttpServletRequest("DELETE", "/kiikii/project/42")).isEmpty());
    }
}
