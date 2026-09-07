package megane6.weplanet.service.admin;


import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.admin.AdminAgencyPermissionResponse;
import megane6.weplanet.domain.entity.AgencyProfile;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.AgencyStatus;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.domain.entity.enumfolder.UserStatus;
import megane6.weplanet.repository.AgencyProfileRepository;
import megane6.weplanet.repository.AgencyRepository;
import megane6.weplanet.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAgencyPermissionService {
	
	private final AgencyProfileRepository apr;
	private final AgencyRepository ar;
	private final UserRepository ur;
	
	public List<AdminAgencyPermissionResponse> getPermissions(
			Boolean approved,
			UserStatus userStatus,
			AgencyStatus agencyStatus,
			String keyword
	) {
		String normalizedKeyword = normalizeKeyword(keyword);
		return apr.searchForAdmin(
				approved, userStatus, agencyStatus, normalizedKeyword)
				.stream()
				.map(this::toResponse)
				.toList();
	}
	
	public AgencyPermissionStats getStats() {
		return new AgencyPermissionStats(
				apr.count(),
				apr.countByApprovedAtIsNull(),
				apr.countByApprovedAtIsNotNull(),
				ar.countByStatus(AgencyStatus.ACTIVE),
				ar.countByStatus(AgencyStatus.SUSPENDED)
		);
	}
	
	@Transactional
	public void approvePermission(Long userId, Long adminId) {
		User admin = requireAdmin(adminId);
		AgencyProfile profile = requireProfile(userId);
		validateApprovalTarget(profile);
		
		profile.approve(admin);
	}
	
	@Transactional
	public void revokePermission(Long userId, Long adminId) {
		User admin = requireAdmin(adminId);
		AgencyProfile profile = requireProfile(userId);
		
		profile.revokeApproval(admin);
	}
	
	private void validateApprovalTarget(AgencyProfile profile) {
		User user = profile.getUser();
		if (user.getRole() != Role.AGENCY) {
			throw new IllegalStateException("소속사 계정만 권한 승인을 받을 수 있습니다.");
		}
		if (user.getStatus() == UserStatus.SUSPENDED) {
			throw new IllegalStateException("정지된 계정은 승인할 수 없습니다.");
		}
		if (user.getStatus() == UserStatus.WITHDRAWN) {
			throw new IllegalStateException("탈퇴한 계정은 승인할 수 없습니다.");
		}
		if (profile.getAgency().getStatus() == AgencyStatus.SUSPENDED) {
			throw new IllegalStateException("운영 정지된 소속사의 계정은 승인할 수 없습니다.");
		}
	}
	
	private AdminAgencyPermissionResponse toResponse(AgencyProfile profile) {
		User user = profile.getUser();
		User approvedBy = profile.getApprovedBy();
		
		return new AdminAgencyPermissionResponse(
				user.getId(),
				user.getUsername(),
				user.getNickname(),
				user.getEmail(),
				
				user.getStatus().name(),
				userStatusLabel(user.getStatus()),
				
				profile.getAgency().getId(),
				profile.getAgency().getName(),
				profile.getAgency().getBusinessNo(),
				profile.getAgency().getCeoName(),
				
				profile.getAgency().getStatus().name(),
				agencyStatusLabel(
						profile.getAgency().getStatus()
				),
				
				profile.getDepartment(),
				profile.getPosition(),
				profile.isOwner(),
				
				profile.isApproved(),
				
				approvedBy == null
						? null
						: approvedBy.getId(),
				
				approvedBy == null
						? null
						: approvedBy.getNickname(),
				
				profile.getApprovedAt(),
				user.getCreatedAt()
		);
	}
	
	private User requireAdmin(Long adminId) {
		User admin = ur.findById(adminId).orElseThrow(() ->
				new IllegalArgumentException("관리자 계정을 찾을 수 없습니다."));
		if (admin.getRole() != Role.ADMIN) {
			throw new IllegalStateException("관리자 권한이 필요합니다.");
		}
		return admin;
	}
	
	private AgencyProfile requireProfile(Long userId) {
		return apr.findByUser_Id(userId).orElseThrow(() ->
				new IllegalArgumentException("소속사 권한 신청을 찾을 수 없습니다."));
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
	
	public record AgencyPermissionStats(
			long totalPermissionCount,
			long pendingCount,
			long approvedCount,
			long activeAgencyCount,
			long suspendedAgencyCount
	) {
	
	}
}
