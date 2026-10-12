package megane6.weplanet.controller.admin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class AdminPaginationTest {
    @AfterEach
    void clearRequest() { RequestContextHolder.resetRequestAttributes(); }

    @Test
    void keywordNavigationShowsTenNumbersAndMovesBetweenGroups() {
        bindRequest("/chat/admin/keywords");
        var first = keywords(0);
        assertEquals(IntStream.rangeClosed(1, 10).boxed().toList(),
                first.links().stream().map(AdminPagination.PageLink::number).toList());
        assertNull(first.previousUrl());
        assertEquals("/chat/admin/keywords?page=10", first.nextUrl());
        var next = keywords(10);
        assertEquals(IntStream.rangeClosed(11, 20).boxed().toList(),
                next.links().stream().map(AdminPagination.PageLink::number).toList());
        assertEquals("/chat/admin/keywords?page=9", next.previousUrl());
        assertEquals("/chat/admin/keywords?page=20", next.nextUrl());
        assertTrue(next.links().getFirst().active());
    }

    @Test
    void lastKeywordGroupOnlyShowsRemainingNumbers() {
        bindRequest("/chat/admin/keywords");
        var last = keywords(323);
        assertEquals(IntStream.rangeClosed(321, 324).boxed().toList(),
                last.links().stream().map(AdminPagination.PageLink::number).toList());
        assertNull(last.nextUrl());
        assertEquals("/chat/admin/keywords?page=319", last.previousUrl());
        assertTrue(last.links().getLast().active());
    }

    @Test
    void otherListsShowFiveNumbersAndPreserveFiltersAndIndependentPages() {
        var request = bindRequest("/admin/communities");
        request.addParameter("keyword", "팀 A+B");
        request.addParameter("pendingPage", "2");
        request.addParameter("page", "7");
        var model = new ExtendedModelMap();
        AdminPagination.addNavigation(model, "adminProjectsPagination", 7, 324, "page");
        var navigation = (AdminPagination.Navigation) model.get("adminProjectsPagination");
        assertEquals(IntStream.rangeClosed(6, 10).boxed().toList(),
                navigation.links().stream().map(AdminPagination.PageLink::number).toList());
        assertEquals("/admin/communities?keyword=%ED%8C%80%20A%2BB&pendingPage=2&page=4", navigation.previousUrl());
        assertEquals("/admin/communities?keyword=%ED%8C%80%20A%2BB&pendingPage=2&page=10", navigation.nextUrl());
        assertTrue(navigation.links().get(2).active());
    }

    @Test
    void emptyListStillHasOneDisabledPageAndRowCountIsUnchanged() {
        bindRequest("/admin/artists");
        var model = new ExtendedModelMap();
        AdminPagination.paginate(model, "artists", java.util.List.of(), 0, 15, "page");
        var navigation = (AdminPagination.Navigation) model.get("artistsPagination");
        assertEquals(1, navigation.links().size());
        assertNull(navigation.previousUrl());
        assertNull(navigation.nextUrl());
    }

    @Test
    void renderedKeywordFooterDoesNotExpandHundredsOfPageNumbers() {
        bindRequest("/chat/admin/keywords");
        var templates = new org.thymeleaf.templateresolver.ClassLoaderTemplateResolver();
        templates.setPrefix("templates/");
        templates.setSuffix(".html");
        var engine = new org.thymeleaf.spring6.SpringTemplateEngine();
        engine.setTemplateResolver(templates);
        var context = new org.thymeleaf.context.Context(java.util.Locale.KOREAN);
        context.setVariable("pagination", keywords(0));
        String html = engine.process("admin/fragments/pagination", context);
        assertTrue(html.contains(">10</a>"));
        assertFalse(html.contains(">11</a>"));
        assertFalse(html.contains(">324</a>"));
        assertTrue(html.contains("/chat/admin/keywords?page=10"));
    }

    private AdminPagination.Navigation keywords(int page) {
        var model = new ExtendedModelMap();
        AdminPagination.paginate(model, "keywords", IntStream.range(0, 324 * 15).boxed().toList(),
                page, 15, "page", "/chat/admin/keywords", AdminPagination.KEYWORD_PAGE_LINKS);
        assertEquals(15, ((java.util.List<?>) model.get("keywords")).size());
        return (AdminPagination.Navigation) model.get("keywordsPagination");
    }

    private MockHttpServletRequest bindRequest(String uri) {
        var request = new MockHttpServletRequest("GET", uri);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        return request;
    }
}
