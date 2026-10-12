package megane6.weplanet.controller.project;

import megane6.weplanet.controller.fan.project.ProjectController;
import megane6.weplanet.domain.dto.ArtistCardView;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.repository.main.UserRepository;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.calendar.ArtistAttendanceService;
import megane6.weplanet.service.community.*;
import megane6.weplanet.service.membership.MembershipService;
import megane6.weplanet.service.portal.PortalManagementService;
import megane6.weplanet.service.project.ProjectService;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectHeaderTest {
    @Test
    void projectListDetailAndMembershipGateKeepCommunityHeaderImage() {
        UserRepository users = mock(UserRepository.class);
        CommunityJoinService joins = mock(CommunityJoinService.class);
        PortalManagementService portal = mock(PortalManagementService.class);
        ProjectController controller = new ProjectController(mock(ProjectService.class), users, joins,
                mock(MembershipService.class), mock(ArtistAttendanceService.class),
                mock(CommunityDrawerHelper.class), portal, null, mock(CommunityArtistResolver.class));
        User artist = mock(User.class);
        when(artist.getId()).thenReturn(10L);
        when(artist.getRole()).thenReturn(Role.ARTIST);
        User fan = mock(User.class);
        when(fan.getId()).thenReturn(1L);
        when(fan.getRole()).thenReturn(Role.FAN);
        when(fan.canParticipateInCommunity()).thenReturn(true);
        when(users.findById(10L)).thenReturn(Optional.of(artist));
        when(users.findById(1L)).thenReturn(Optional.of(fan));
        when(users.findByRole(Role.ARTIST)).thenReturn(List.of(artist));
        when(portal.toArtistCard(artist)).thenReturn(new ArtistCardView(10L, "아티스트", "AR", null, "/community/10"));
        when(portal.toArtistCards(List.of(artist))).thenReturn(List.of());
        when(portal.findHeaderImageUrl(artist)).thenReturn("/uploads/header.png");
        AuthenticatedUser principal = AuthenticatedUser.builder().id(1L).build();
        when(joins.isJoined(fan, 10L)).thenReturn(true);
        ExtendedModelMap list = new ExtendedModelMap();
        ExtendedModelMap detail = new ExtendedModelMap();
        ExtendedModelMap gate = new ExtendedModelMap();

        assertEquals("community/project", controller.projectPage(10L, "deadline", principal, list));
        assertEquals("community/project-detail", controller.projectDetail(10L, 20L, principal, detail));
        when(joins.isJoined(fan, 10L)).thenReturn(false);
        assertEquals("community/membership-required", controller.projectPage(10L, "deadline", principal, gate));
        for (var model : List.of(list, detail, gate)) {
            assertEquals("/uploads/header.png", model.get("artistHeaderImageUrl"));
        }
    }
}
