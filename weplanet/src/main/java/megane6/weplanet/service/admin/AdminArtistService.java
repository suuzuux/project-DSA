package megane6.weplanet.service.admin;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.admin.AdminArtistResponse;
import megane6.weplanet.domain.entity.Agency;
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
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
		List<User> artists = ur.searchArtistsForAdmin(
				Role.ARTIST,
				userStatus,
				agencyStatus
		);

		Map<Long, ArtistAccountProfile> profilesByUserId = loadProfiles(artists);

		return artists.stream()
				.filter(user -> matchesKeyword(
						user,
						profilesByUserId.get(user.getId()),
						normalizedKeyword
				))
				.map(user -> toResponse(
						user,
						profilesByUserId.get(user.getId())
				))
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
	public void suspendArtist(Long userId, Long adminId, String ipAddress) {
		requireArtistUser(userId);
		aus.suspendUser(userId, adminId, ipAddress);
	}
	
	@Transactional
	public void reinstateArtist(Long userId, Long adminId, String ipAddress) {
		requireArtistUser(userId);
		aus.reinstateUser(userId, adminId, ipAddress);
	}
	
	private Map<Long, ArtistAccountProfile> loadProfiles(List<User> artists) {
		if (artists.isEmpty()) {
			return Map.of();
		}

		List<Long> userIds = artists.stream()
				.map(User::getId)
				.toList();

		return aapr.findAllByUserIds(userIds)
				.stream()
				.collect(Collectors.toMap(
						ArtistAccountProfile::getUserId,
						Function.identity()
				));
	}

	private AdminArtistResponse toResponse(
			User user,
			ArtistAccountProfile profile
	) {
		Agency agency = profile == null
				? user.getAgency()
				: profile.getAgency();

		return new AdminArtistResponse(
				user.getId(),
				user.getUsername(),
				user.getNickname(),
				user.getEmail(),
				
				profile == null ? user.getNickname() : profile.getStageName(),
				profile == null ? null : profile.getDebutDate(),
				profile == null ? null : profile.getPosition(),
				profile == null ? null : profile.getProfileImg(),
				
				user.getStatus().name(),
				userStatusLabel(user.getStatus()),
				
				agency == null ? null : agency.getId(),
				agency == null ? null : agency.getName(),
				
				agency == null ? null : agency.getStatus().name(),
				agency == null
						? "admin.artists.unregistered"
						: agencyStatusLabel(agency.getStatus()),
				
				user.getEmailVerifiedAt() != null,
				user.getCreatedAt(),
				user.getLastLoginAt()
		);
	}
	
	private User requireArtistUser(Long userId) {
		User user = ur.findById(userId).orElseThrow(() ->
				new IllegalArgumentException("admin.error.artist.notFound"));

		if (user.getRole() != Role.ARTIST) {
			throw new IllegalArgumentException("admin.error.artist.artistOnly");
		}

		return user;
	}

	private boolean matchesKeyword(
			User user,
			ArtistAccountProfile profile,
			String keyword
	) {
		if (keyword == null) {
			return true;
		}

		Agency agency = profile == null
				? user.getAgency()
				: profile.getAgency();
		String lowerKeyword = keyword.toLowerCase(Locale.ROOT);

		return contains(user.getUsername(), lowerKeyword)
				|| contains(user.getNickname(), lowerKeyword)
				|| contains(user.getEmail(), lowerKeyword)
				|| contains(profile == null ? null : profile.getStageName(), lowerKeyword)
				|| contains(profile == null ? null : profile.getPosition(), lowerKeyword)
				|| contains(agency == null ? null : agency.getName(), lowerKeyword);
	}

	private boolean contains(String value, String lowerKeyword) {
		return value != null
				&& value.toLowerCase(Locale.ROOT).contains(lowerKeyword);
	}
	
	private String normalizeKeyword(String keyword) {
		if (keyword == null || keyword.isBlank()) {
			return null;
		}
		
		return keyword.trim();
	}
	
	private String userStatusLabel(UserStatus status) {
		return switch (status) {
			case ACTIVE -> "admin.agencies.userStatus.ACTIVE";
			case DORMANT -> "admin.agencies.userStatus.DORMANT";
			case SUSPENDED -> "admin.agencies.userStatus.SUSPENDED";
			case WITHDRAWN -> "admin.agencies.userStatus.WITHDRAWN";
			case PENDING_ACTIVATION -> "admin.agencies.userStatus.PENDING_ACTIVATION";
		};
	}
	
	private String agencyStatusLabel(AgencyStatus status) {
		return switch (status) {
			case ACTIVE -> "admin.agencies.agencyStatus.ACTIVE";
			case SUSPENDED -> "admin.agencies.agencyStatus.SUSPENDED";
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
