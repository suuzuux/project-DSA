package megane6.weplanet.controller.project;

import megane6.weplanet.domain.dto.ArtistCardView;
import megane6.weplanet.domain.dto.ProjectCardView;
import megane6.weplanet.domain.dto.ProjectRequestDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProjectSlugRenderingTest {
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var messages = new ResourceBundleMessageSource();
        messages.setBasename("messages"); messages.setDefaultEncoding("UTF-8");
        var templates = new ClassLoaderTemplateResolver();
        templates.setPrefix("templates/"); templates.setSuffix(".html"); templates.setCharacterEncoding("UTF-8");
        var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(templates); engine.setTemplateEngineMessageSource(messages);
        var views = new ThymeleafViewResolver();
        views.setTemplateEngine(engine); views.setCharacterEncoding("UTF-8");
        mvc = MockMvcBuilders.standaloneSetup(new Preview()).setViewResolvers(views).build();
    }

    @Test
    void projectCardsAndFanNavigationUseSlug() throws Exception {
        mvc.perform(get("/preview/cards"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/kiikii/project/42\"")))
                .andExpect(content().string(containsString("data-modal-url=\"/kiikii/project/42/modal\"")))
                .andExpect(content().string(containsString("data-contribution-url=\"/kiikii/project/42/contributions\"")))
                .andExpect(content().string(not(containsString("/community/1101"))));
        mvc.perform(get("/preview/fan"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/kiikii/project\"")))
                .andExpect(content().string(not(containsString("/community/1101"))));
    }

    @Test
    void createFormAndVerificationUseSlug() throws Exception {
        mvc.perform(get("/preview/form"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("action=\"/kiikii/project\"")))
                .andExpect(content().string(containsString("data-eligibility-url=\"/kiikii/project/eligibility\"")))
                .andExpect(content().string(containsString("data-url=\"/kiikii/project/email-verification\"")))
                .andExpect(content().string(containsString("data-url=\"/kiikii/project/email-verification/confirm\"")));
    }

    @Controller
    static class Preview {
        @GetMapping("/preview/{part}")
        String render(@PathVariable String part, Model model) {
            model.addAttribute("artist", new ArtistCardView(1101L, "KIIKII", "KI", null, "/kiikii"));
            model.addAttribute("isOwnCommunity", false);
            model.addAttribute("boardType", megane6.weplanet.domain.entity.BoardType.FAN);
            model.addAttribute("sort", "latest");
            model.addAttribute("registeredEmail", "fan@test.com");
            model.addAttribute("accountHolderName", "팬");
            var form = new ProjectRequestDTO();
            form.setArtistId(1101L);
            model.addAttribute("projectRequestDTO", form);
            model.addAttribute("eventTypes", megane6.weplanet.domain.entity.enumfolder.FanProjectEventType.values());
            model.addAttribute("settlementBanks", megane6.weplanet.domain.entity.enumfolder.SettlementBank.values());
            model.addAttribute("projects", List.of(new ProjectCardView(42L, "프로젝트", "이벤트", "모금중", "funding",
                    10000L, LocalDate.now(), LocalDate.now().plusDays(1), null, "팬", 1, 0L, 0L, 0)));
            return switch(part) {
                case "fan" -> "community/fan :: .board-toolbar";
                case "form" -> "community/project :: #projectCreateForm";
                default -> "community/project :: .project-grid";
            };
        }
    }
}
