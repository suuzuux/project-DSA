package megane6.weplanet.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.ProjectContributionRequestDTO;
import megane6.weplanet.domain.dto.ProjectContributionResult;
import megane6.weplanet.exception.AuthenticationRequiredException;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.ProjectContributionService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/community/{artistId}/project/{projectId}/contributions")
public class ProjectContributionController {

    private final ProjectContributionService contributionService;

    @PostMapping
    public ProjectContributionResult contribute(
            @PathVariable Long artistId,
            @PathVariable Long projectId,
            @Valid @RequestBody ProjectContributionRequestDTO request,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        if (principal == null) {
            throw new AuthenticationRequiredException();
        }
        return contributionService.contribute(principal.getId(), artistId, projectId, request);
    }
}
