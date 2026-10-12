package megane6.weplanet.controller.project;

import megane6.weplanet.controller.fan.project.ProjectController;
import megane6.weplanet.controller.fan.payment.TossPaymentController;
import megane6.weplanet.domain.dto.*;
import megane6.weplanet.i18n.Messages;
import megane6.weplanet.repository.main.UserRepository;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.community.*;
import megane6.weplanet.service.project.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectSlugLinksTest {
    @Test
    void registrationAndReviewRedirectToCommunitySlug() {
        var urls = mock(CommunityUrls.class);
        when(urls.of(1101L, "project")).thenReturn("/kiikii/project");
        var projects = mock(ProjectService.class);
        var controller = new ProjectController(projects, mock(UserRepository.class), null,
                null, null, null, null, mock(Messages.class), mock(CommunityArtistResolver.class), urls);
        var principal = AuthenticatedUser.builder().id(1L).build();
        var form = new ProjectRequestDTO();
        when(projects.createProject(1L, form)).thenReturn(42L);
        assertEquals("redirect:/kiikii/project", controller.createProject(1101L, form,
                mock(BindingResult.class), principal, new ExtendedModelMap(), new RedirectAttributesModelMap()));
        assertEquals(1101L, form.getArtistId());
        assertEquals("redirect:/kiikii/project/42", controller.approveProject(1101L, 42L,
                new MockHttpServletRequest(), principal, new RedirectAttributesModelMap()));
        assertEquals("redirect:/kiikii/project/42", controller.rejectProject(1101L, 42L, "사유",
                new MockHttpServletRequest(), principal, new RedirectAttributesModelMap()));
    }

    @Test
    void paymentReturnLinkUsesCommunitySlug() {
        var payments = mock(ProjectContributionService.class);
        var urls = mock(CommunityUrls.class);
        when(urls.of(1101L, "project")).thenReturn("/kiikii/project");
        var result = mock(ProjectPaymentResultView.class);
        when(result.artistId()).thenReturn(1101L);
        when(payments.confirmVirtualAccount(1L, "key", "order", 5000L)).thenReturn(result);
        var controller = new TossPaymentController(payments, mock(Messages.class), urls);
        var model = new ExtendedModelMap();
        assertEquals("payment/virtual-account", controller.success("key", "order", 5000L,
                AuthenticatedUser.builder().id(1L).build(), model));
        assertEquals("/kiikii/project", model.get("projectUrl"));
    }
}
