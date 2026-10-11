package megane6.weplanet.controller;

import megane6.weplanet.controller.common.AuthenticatedUserResolver;
import megane6.weplanet.controller.notice.SiteNoticeController;
import megane6.weplanet.domain.entity.SiteNotice;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.NoticeCategory;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.notice.SiteNoticeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.thymeleaf.extras.springsecurity6.dialect.SpringSecurityDialect;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.List;
import java.util.Locale;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// 실제 전체공지 템플릿을 렌더링해 패키지 이동 후에도 목록·통계가 열리는지 검증한다.
class SiteNoticePageTest {
    private final SiteNoticeService notices = mock(SiteNoticeService.class);
    private final AuthenticatedUserResolver users = mock(AuthenticatedUserResolver.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AuthenticatedUser principal = AuthenticatedUser.builder().id(1L).username("admin")
                .nickname("관리자").roleName("ROLE_ADMIN").enabled(true).build();
        User admin = mock(User.class);
        when(admin.getRole()).thenReturn(Role.ADMIN);
        when(users.requireAuthenticated(principal)).thenReturn(admin);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));

        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        ClassLoaderTemplateResolver templates = new ClassLoaderTemplateResolver();
        templates.setPrefix("templates/");
        templates.setSuffix(".html");
        templates.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(templates);
        engine.setTemplateEngineMessageSource(messages);
        engine.addDialect(new SpringSecurityDialect());
        ThymeleafViewResolver views = new ThymeleafViewResolver();
        views.setTemplateEngine(engine);
        views.setCharacterEncoding("UTF-8");
        mockMvc = MockMvcBuilders.standaloneSetup(new SiteNoticeController(notices, null, users, null, null, null))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setViewResolvers(views).build();
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void emptyNoticeListRendersPinLimitAndEmptyState() throws Exception {
        when(notices.listAll(null, null, 0, 10))
                .thenReturn(new SiteNoticeService.PageResult<>(List.of(), 0, 10, 0));
        when(notices.getStats()).thenReturn(new SiteNoticeService.NoticeStats(0, 0, 0, 0));

        mockMvc.perform(get("/admin/notices").locale(Locale.KOREAN))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/notices"))
                .andExpect(model().attribute("maxPinned", SiteNoticeService.MAX_PINNED))
                .andExpect(content().string(containsString("0 / 3")))
                .andExpect(content().string(containsString("최대 3개")))
                .andExpect(content().string(containsString("등록된 공지가 없습니다.")));
    }

    @Test
    void populatedNoticeListRendersTitlesCategoriesAndManagementLinks() throws Exception {
        SiteNotice notice = SiteNotice.create(null, NoticeCategory.GENERAL, "서비스 운영 안내", "공지 본문", true, null);
        ReflectionTestUtils.setField(notice, "id", 7L);
        notice.prePersist();
        notice.applyPin(true, 1);
        when(notices.listAll(null, null, 0, 10))
                .thenReturn(new SiteNoticeService.PageResult<>(List.of(notice), 0, 10, 1));
        when(notices.getStats()).thenReturn(new SiteNoticeService.NoticeStats(1, 0, 0, 1));

        mockMvc.perform(get("/admin/notices").locale(Locale.KOREAN))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("1 / 3")))
                .andExpect(content().string(containsString("서비스 운영 안내")))
                .andExpect(content().string(containsString("/admin/notices/7/edit")))
                .andExpect(content().string(containsString("/admin/notices/7/delete")))
                .andExpect(content().string(containsString("js-pinned")));
    }
}
