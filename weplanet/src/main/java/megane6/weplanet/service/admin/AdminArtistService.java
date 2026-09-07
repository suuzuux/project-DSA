package megane6.weplanet.service.admin;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.admin.AdminArtistResponse;
import megane6.weplanet.domain.entity.ArtistAccountProfile;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.AgencyStatus;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.domain.entity.enumfolder.UserStatus;
import megane6.weplanet.repository.ArtistAccountProfileRepository;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.service.AdminUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminArtistService {
	
	private final ArtistAccountProfileRepository aapr;
	private final UserRepository ur;
	private final AdminUserService aus;
	
	public List<AdminArtistResponse> getArtists(
			UserStatus userStatus,
			AgencyStatus agencyStatus,
			String keyword) {
		String normalizedKeyword = normalizeKeyword(keyword);
		return aapr.searchForAdmin(userStatus, agencyStatus, normalizedKeyword)
				.stream()
				.map(this::toResponse)
				.toList();
	}
	
	public ArtistStats getStats() {
		return new ArtistStats(
				ur.countByRole(Role.ARTIST),
				aapr.count(),
				ur.countByRoleAndStatus(Role.ARTIST, UserStatus.ACTIVE),
				ur.countByRoleAndStatus(Role.ARTIST, UserStatus.DORMANT),
				ur.countByRoleAndStatus(Role.ARTIST, UserStatus.SUSPENDED),
				ur.countByRoleAndStatus(Role.ARTIST, UserStatus.WITHDRAWN)
		);
	}
	
	@Transactional
	public void suspendArtist(Long userId, Long adminId) {
		requireArtistProfile(userId);
		aus.suspendUser(userId, adminId);
	}
	
	@Transactional
	public void reinstateArtist(Long userId, Long adminId) {
		requireArtistProfile(userId);
		aus.reinstateUser(userId, adminId);
	}
	
	private AdminArtistResponse toResponse(ArtistAccountProfile profile) {
		User user = profile.getUser();
		return new AdminArtistResponse(
				user.getId(),
				user.getUsername(),
				user.getNickname(),
				user.getEmail(),
				
				profile.getStageName(),
				profile.getDebutDate(),
				profile.getPosition(),
				profile.getProfileImg(),
				
				user.getStatus().name(),
				userStatusLabel(user.getStatus()),
				
				profile.getAgency().getId(),
				profile.getAgency().getName(),
				
				profile.getAgency().getStatus().name(),
				agencyStatusLabel(profile.getAgency().getStatus()),
				
				user.getEmailVerifiedAt() != null,
				user.getCreatedAt(),
				user.getLastLoginAt()
		);
	}
	
	private ArtistAccountProfile requireArtistProfile(Long userId) {
		return aapr.findByUser_Id(userId).orElseThrow(() ->
				new IllegalArgumentException("아티스트 프로필을 찾을 수 없습니다."));
	}
	
	private String normalizeKeyword(String keyword) {
		if (keyword == null || keyword.isBlank()) {
			return null;
		}
		
		return keyword.trim();
	}
	
	private String userStatusLabel(UserStatus status) {
		return switch (status) {
			case ACTIVE -> "활성";
			case DORMANT -> "휴면";
			case SUSPENDED -> "정지";
			case WITHDRAWN -> "탈퇴";
		};
	}
	
	private String agencyStatusLabel(AgencyStatus status) {
		return switch (status) {
			case ACTIVE -> "운영 중";
			case SUSPENDED -> "운영 정지";
		};
	}
	
	public record ArtistStats(
			long totalArtistCount,
			long registeredProfileCount,
			long activeCount,
			long dormantCount,
			long suspendedCount,
			long withdrawnCount
	) {
	
	}
}
