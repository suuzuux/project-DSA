package megane6.weplanet.controller.admin;

import megane6.weplanet.domain.dto.admin.AdminArtistResponse;
import megane6.weplanet.domain.entity.enumfolder.FanProjectStatus;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.admin.AdminArtistService;
import megane6.weplanet.service.admin.AdminCommunityService;
import megane6.weplanet.service.project.ProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminListPaginationTest {
    private final AdminCommunityService projects = mock(AdminCommunityService.class);
    private final AdminArtistService artists = mock(AdminArtistService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var principal = AuthenticatedUser.builder().id(999L).roleName("ROLE_ADMIN").build();
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        var now = LocalDateTime.now();
        when(projects.getStats()).thenReturn(new AdminCommunityService.CommunityStats(20, 12, 12, 0));
        when(projects.getAdminProjects(null, null)).thenReturn(IntStream.rangeClosed(1, 27).mapToObj(n ->
                new AdminCommunityService.AdminProjectItem((long)n, "프로젝트" + n, "이벤트", "팀", "팬",
                        "FUNDING", "모금중", 10000L, 0L, 0L, 0, now, now.plusDays(1), null, null, null, now)).toList());
        when(projects.getPendingProjects()).thenReturn(IntStream.rangeClosed(1, 12).mapToObj(n ->
                new AdminCommunityService.PendingProjectItem((long)n, "대기" + n, "이벤트", "팀", "팬",
                        10000L, now, now.plusDays(1), now)).toList());
        when(artists.getStats()).thenReturn(new AdminArtistService.ArtistStats(34, 34, 34, 0, 0, 0));
        when(artists.getArtists(null, null, null)).thenReturn(IntStream.rangeClosed(1, 34).mapToObj(n ->
                new AdminArtistResponse((long)n, "artist" + n, "팀" + n, n + "@test.com", "팀" + n,
                        null, null, null, "ACTIVE", "admin.agencies.userStatus.ACTIVE", null, null,
                        null, "admin.artists.unregistered", true, now, null, List.of())).toList());
        var messages = new ResourceBundleMessageSource();
        messages.setBasename("messages"); messages.setDefaultEncoding("UTF-8");
        var templates = new ClassLoaderTemplateResolver();
        templates.setPrefix("templates/"); templates.setSuffix(".html"); templates.setCharacterEncoding("UTF-8");
        var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(templates); engine.setTemplateEngineMessageSource(messages);
        var views = new ThymeleafViewResolver();
        views.setTemplateEngine(engine); views.setCharacterEncoding("UTF-8");
        mvc = MockMvcBuilders.standaloneSetup(new AdminCommunityController(projects, mock(ProjectService.class), null),
                        new AdminArtistController(artists, null))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).setViewResolvers(views).build();
    }

    @AfterEach
    void clearAuth() { SecurityContextHolder.clearContext(); }

    @Test
    void projectsHaveTenRowsAndIndependentPendingPagination() throws Exception {
        var response = mvc.perform(get("/admin/communities").param("page", "1").param("pendingPage", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("프로젝트11")))
                .andExpect(content().string(containsString("pendingPage=1&amp;page=2")))
                .andReturn();
        var model = response.getModelAndView().getModel();
        assertEquals(10, ((List<?>)model.get("adminProjects")).size());
        assertEquals(2, ((List<?>)model.get("pendingProjects")).size());
        assertEquals(3, ((AdminPagination.Navigation) model.get("adminProjectsPagination")).totalPages());
        assertFalse(response.getResponse().getContentAsString().contains("프로젝트21"));
    }

    @Test
    void artistsHaveFifteenRowsAndLastPageContainsRemainingFour() throws Exception {
        var first = mvc.perform(get("/admin/artists")).andExpect(status().isOk()).andReturn();
        assertEquals(15, ((List<?>)first.getModelAndView().getModel().get("artists")).size());
        var last = mvc.perform(get("/admin/artists").param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("aria-current=\"page\">3</span>"))).andReturn();
        assertEquals(4, ((List<?>)last.getModelAndView().getModel().get("artists")).size());
    }
}
