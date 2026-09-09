package megane6.weplanet.service.portal;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.service.MembershipService;
import megane6.weplanet.service.community.CommunityJoinService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 에이전시 계정이 소속 아티스트 커뮤니티·멤버십에 자동 가입되도록 처리.
 */
@Service
@RequiredArgsConstructor
public class AgencyEnrollmentService {

	private final UserRepository userRepository;
	private final CommunityJoinService communityJoinService;
	private final MembershipService membershipService;

	@Transactional
	public void enrollManagedArtists(User agencyUser) {
		if (agencyUser == null || agencyUser.getRole() != Role.AGENCY) {
			return;
		}
		Long agencyId = agencyUser.agencyId();
		if (agencyId == null) {
			return;
		}
		List<User> artists = userRepository.findByRoleAndAgency_Id(Role.ARTIST, agencyId);
		if (artists.isEmpty()) {
			return;
		}
		String nickname = communityNickname(agencyUser);
		for (User artist : artists) {
			communityJoinService.ensureJoined(agencyUser, artist.getId(), nickname);
			membershipService.join(agencyUser, artist);
		}
	}

	private String communityNickname(User agencyUser) {
		String raw = agencyUser.getNickname();
		if (raw == null || raw.isBlank()) {
			return "Agency";
		}
		String trimmed = raw.trim();
		return trimmed.length() <= 10 ? trimmed : trimmed.substring(0, 10);
	}
}
