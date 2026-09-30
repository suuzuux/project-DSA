package megane6.weplanet.service.admin;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.admin.AdminArtistResponse;
import megane6.weplanet.domain.entity.Agency;
import megane6.weplanet.domain.entity.ArtistAccountProfile;
import megane6.weplanet.domain.entity.GroupMember;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.AgencyStatus;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.domain.entity.enumfolder.UserStatus;
import megane6.weplanet.repository.ArtistAccountProfileRepository;
import megane6.weplanet.repository.GroupMemberRepository;
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
	private final GroupMemberRepository gmr;
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
		Map<Long, List<AdminArtistResponse.Member>> membersByGroupId = loadMembers(artists);

		return artists.stream()
				.map(user -> toResponse(
						user,
						profilesByUserId.get(user.getId()),
						membersByGroupId.getOrDefault(user.getId(), List.of())
				))
				.filter(response -> matchesKeyword(response, normalizedKeyword))
				.toList();
	}

	public ArtistStats getStats() {
		return new ArtistStats(
				ur.countByRole(Role.ARTIST),
				aapr.countByUser_Role(Role.ARTIST),
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

	// 그룹 id(= 그룹 계정 users.id) -> 활동 중인 멤버 목록. 멤버 활동명·포지션은 멤버의 artist_profiles 에서 가져온다
	private Map<Long, List<AdminArtistResponse.Member>> loadMembers(List<User> artists) {
		if (artists.isEmpty()) {
			return Map.of();
		}

		List<Long> groupIds = artists.stream()
				.map(User::getId)
				.toList();

		List<GroupMember> groupMembers = gmr.findByGroupIdInAndLeftAtIsNullOrderByIdAsc(groupIds);

		if (groupMembers.isEmpty()) {
			return Map.of();
		}

		List<Long> memberIds = groupMembers.stream()
				.map(groupMember -> groupMember.getMember().getId())
				.toList();

		Map<Long, ArtistAccountProfile> memberProfiles = aapr.findAllByUserIds(memberIds)
				.stream()
				.collect(Collectors.toMap(
						ArtistAccountProfile::getUserId,
						Function.identity()
				));

		// groupingBy + toList 는 들어온 순서(id 오름차순 = 등록 순)를 유지한다
		return groupMembers.stream()
				.collect(Collectors.groupingBy(
						GroupMember::getGroupId,
						Collectors.mapping(
								groupMember -> toMember(
										groupMember,
										memberProfiles.get(groupMember.getMember().getId())
								),
								Collectors.toList()
						)
				));
	}

	private AdminArtistResponse.Member toMember(
			GroupMember groupMember,
			ArtistAccountProfile profile
	) {
		User member = groupMember.getMember();

		return new AdminArtistResponse.Member(
				member.getId(),
				profile == null ? member.getNickname() : profile.getStageName(),
				profile == null ? null : profile.getPosition(),
				member.getStatus().name(),
				userStatusLabel(member.getStatus()),
				groupMember.getJoinedAt()
		);
	}

	private AdminArtistResponse toResponse(
			User user,
			ArtistAccountProfile profile,
			List<AdminArtistResponse.Member> members
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
				user.getLastLoginAt(),

				members
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

	// 멤버 활동명·포지션으로 검색해도 그 멤버가 속한 그룹이 나온다
	private boolean matchesKeyword(
			AdminArtistResponse artist,
			String keyword
	) {
		if (keyword == null) {
			return true;
		}

		String lowerKeyword = keyword.toLowerCase(Locale.ROOT);

		return contains(artist.username(), lowerKeyword)
				|| contains(artist.nickname(), lowerKeyword)
				|| contains(artist.email(), lowerKeyword)
				|| contains(artist.stageName(), lowerKeyword)
				|| contains(artist.position(), lowerKeyword)
				|| contains(artist.agencyName(), lowerKeyword)
				|| artist.members().stream().anyMatch(member ->
						contains(member.stageName(), lowerKeyword)
								|| contains(member.position(), lowerKeyword));
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
