package megane6.weplanet.controller.admin;

import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.domain.entity.enumfolder.UserStatus;
import megane6.weplanet.repository.main.UserRepository;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.admin.AdminUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.List;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminUsersPageTest {
    private final UserRepository users = mock(UserRepository.class);
    private final AdminUserService service = new AdminUserService(users, null);
    private MockMvc mvc;
    private List<User> members;

    @BeforeEach
    void setUp() {
        members = IntStream.rangeClosed(1, 110).mapToObj(n -> {
            User user = User.createFan("member" + n, "encoded", "회원" + n, "팬" + n, n + "@test.com");
            ReflectionTestUtils.setField(user, "id", (long) n);
            user.prePersist();
            return user;
        }).toList();
        when(users.searchForAdmin(any(), any(), any(), anyBoolean(), any(), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Pageable page = invocation.getArgument(5);
                    assertEquals(15, page.getPageSize());
                    int start = Math.min(members.size(), (int) page.getOffset());
                    int end = Math.min(members.size(), start + page.getPageSize());
                    return new PageImpl<>(members.subList(start, end), page, members.size());
                });
        when(users.count()).thenReturn(110L);
        AuthenticatedUser principal = AuthenticatedUser.builder().id(999L).username("admin")
                .roleName("ROLE_ADMIN").enabled(true).build();
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
        ThymeleafViewResolver views = new ThymeleafViewResolver();
        views.setTemplateEngine(engine);
        views.setCharacterEncoding("UTF-8");
        mvc = MockMvcBuilders.standaloneSetup(new AdminUserController(service, null))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setViewResolvers(views).build();
    }

    @AfterEach
    void clearAuthentication() { SecurityContextHolder.clearContext(); }

    @Test
    void firstPageOf110MembersShows15RowsAndEightPages() throws Exception {
        var result = mvc.perform(get("/admin/users"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"is-active\" aria-current=\"page\">1</span>")))
                .andExpect(content().string(containsString("110")))
                .andReturn();
        assertEquals(15, ((List<?>) result.getModelAndView().getModel().get("users")).size());
        assertFalse(result.getResponse().getContentAsString().contains("@member16"));
    }

    @Test
    void secondPageKeepsFiltersInNavigationAndActionForms() throws Exception {
        mvc.perform(get("/admin/users").param("page", "1").param("role", "FAN")
                        .param("status", "ACTIVE").param("provider", "LOCAL").param("keyword", "member"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"is-active\" aria-current=\"page\">2</span>")))
                .andExpect(content().string(containsString("@member16")))
                .andExpect(content().string(not(containsString("@member31"))))
                .andExpect(content().string(containsString("role=FAN&amp;status=ACTIVE&amp;provider=LOCAL&amp;keyword=member&amp;page=2")))
                .andExpect(content().string(containsString("name=\"page\" value=\"1\"")));
        verify(users).searchForAdmin(eq(Role.FAN), eq(UserStatus.ACTIVE), isNull(), eq(true), eq("member"), any(Pageable.class));
    }

    @Test
    void navigationPreservesSpecialCharactersInSearchKeyword() throws Exception {
        mvc.perform(get("/admin/users").param("keyword", "팀 A+B & C"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("A%2BB%20%26%20C&amp;page=1")));
    }

    @Test
    void outOfRangePageReturnsLastPageAndEmptyResultShowsOnePage() throws Exception {
        var last = mvc.perform(get("/admin/users").param("page", "999"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("class=\"is-active\" aria-current=\"page\">8</span>"))).andReturn();
        assertEquals(5, ((List<?>) last.getModelAndView().getModel().get("users")).size());
        members = List.of();
        mvc.perform(get("/admin/users").param("page", "9"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("class=\"is-active\" aria-current=\"page\">1</span>")));
    }
}
