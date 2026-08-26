package megane6.weplanet.service;

import megane6.weplanet.domain.entity.Project;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.FanProjectEventType;
import megane6.weplanet.domain.entity.enumfolder.FanProjectStatus;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.repository.FanBadgeOwnershipRepository;
import megane6.weplanet.repository.ProjectImageRepository;
import megane6.weplanet.repository.ProjectRepository;
import megane6.weplanet.repository.ProjectSettlementAccountRepository;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceVisibilityTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private FanBadgeOwnershipRepository badgeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ProjectImageRepository imageRepository;
    @Mock
    private ProjectSettlementAccountRepository settlementAccountRepository;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private AccountProtectionService accountProtectionService;

    @InjectMocks
    private ProjectService projectService;

    @Mock
    private User artist;
    @Mock
    private User creator;
    @Mock
    private User admin;

    @BeforeEach
    void setUp() {
        lenient().when(artist.getId()).thenReturn(7L);
        lenient().when(artist.getRole()).thenReturn(Role.ARTIST);
        lenient().when(creator.getId()).thenReturn(10L);
        lenient().when(creator.getNickname()).thenReturn("개설자");
        lenient().when(imageRepository.findByProject_IdIn(anyCollection())).thenReturn(List.of());
    }

    @Test
    void anonymousUserOnlySeesPublicStatus() {
        Project project = pendingProject();
        when(projectRepository.findByArtistAndDeletedAtIsNull(artist)).thenReturn(List.of(project));

        assertEquals(0, projectService.getProjectCards(artist, ProjectService.SORT_DEADLINE, null).size());

        when(admin.getRole()).thenReturn(Role.ADMIN);
        project.approve(admin);

        assertEquals(1, projectService.getProjectCards(artist, ProjectService.SORT_DEADLINE, null).size());
    }

    @Test
    void fanCanSeeOwnPendingProjectButOtherFanCannot() {
        Project project = pendingProject();
        when(projectRepository.findByArtistAndDeletedAtIsNull(artist)).thenReturn(List.of(project));

        assertEquals(1, projectService.getProjectCards(artist, ProjectService.SORT_DEADLINE, viewer(10L, Role.FAN)).size());
        assertEquals(0, projectService.getProjectCards(artist, ProjectService.SORT_DEADLINE, viewer(11L, Role.FAN)).size());
    }

    @Test
    void adminSeesEveryStatus() {
        Project project = pendingProject();
        when(projectRepository.findByArtistAndDeletedAtIsNull(artist)).thenReturn(List.of(project));

        assertEquals(1, projectService.getProjectCards(artist, ProjectService.SORT_DEADLINE, viewer(1L, Role.ADMIN)).size());
    }

    @Test
    void artistAndAgencyCannotEnterProjectArea() {
        assertThrows(
                IllegalStateException.class,
                () -> projectService.assertProjectAreaAccessible(viewer(7L, Role.ARTIST))
        );
        assertThrows(
                IllegalStateException.class,
                () -> projectService.assertProjectAreaAccessible(viewer(8L, Role.AGENCY))
        );
    }

    @Test
    void adminCanApprovePendingProject() {
        Project project = pendingProject();
        when(admin.getRole()).thenReturn(Role.ADMIN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(projectRepository.findById(30L)).thenReturn(Optional.of(project));

        projectService.approveProject(30L, 7L, 1L);

        assertEquals(FanProjectStatus.APPROVED, project.getStatus());
        assertEquals(admin, project.getReviewedBy());
    }

    private Project pendingProject() {
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        return Project.createPending(
                artist,
                creator,
                "테스트 프로젝트",
                FanProjectEventType.ETC,
                100_000L,
                start,
                start.plusDays(7),
                "프로젝트 상세 설명",
                1,
                5,
                LocalDateTime.now()
        );
    }

    private AuthenticatedUser viewer(Long id, Role role) {
        return AuthenticatedUser.builder()
                .id(id)
                .username("viewer-" + id)
                .roleName(role.authority())
                .enabled(true)
                .build();
    }
}
