package megane6.weplanet.controller;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.ArtistCardView;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.service.portal.PortalManagementService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ScheduleApiController {

	private final PortalManagementService portalManagementService;
	private final UserRepository userRepository;

	@GetMapping("/schedules")
	public Map<String, Object> schedules() {
		List<Map<String, String>> communities = userRepository.findByRole(Role.ARTIST).stream()
				.map(artist -> Map.of(
						"id", String.valueOf(artist.getId()),
						"name", artist.getNickname()
				))
				.toList();

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("communities", communities);
		body.put("eventsByDate", portalManagementService.getPublicEventsByDate());
		return body;
	}

	@GetMapping("/artists")
	public List<ArtistCardView> artists() {
		return userRepository.findByRole(Role.ARTIST).stream()
				.map(ArtistCardView::from)
				.toList();
	}
}
