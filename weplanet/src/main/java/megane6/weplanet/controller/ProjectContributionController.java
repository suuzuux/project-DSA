package megane6.weplanet.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.ProjectContributionRequestDTO;
import megane6.weplanet.domain.dto.ProjectPaymentPrepareResponse;
import megane6.weplanet.exception.AuthenticationRequiredException;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.ProjectContributionService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/community/{artistId}/project/{projectId}/contributions")
public class ProjectContributionController {

    private final ProjectContributionService pcService;
    
    // [참여하기] -> 주문 생성 + 토스 결제창 정보 반환
    @PostMapping
    public ProjectPaymentPrepareResponse contribute(
            @PathVariable Long artistId,
            @PathVariable Long projectId,
            @Valid
            @RequestBody ProjectContributionRequestDTO request,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        if (principal == null) {
            throw new AuthenticationRequiredException();
        }
        
        return pcService.contribute(principal.getId(), artistId, projectId, request);
    }
}
