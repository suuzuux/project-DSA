package megane6.weplanet.service.admin;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.AdminUserListItemResponse;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.*;
import megane6.weplanet.repository.main.UserRepository;
import megane6.weplanet.service.admin.AdminActionLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserService {
	private final UserRepository ur;
	private final AdminActionLogService actionLogService;
	
	public List<AdminUserListItemResponse> getUsers(
			Role role,
			UserStatus status,
			AuthProvider provider,
			boolean localOnly,
			String keyword
	) {
		String normalizeKeyword = normalizeKeyword(keyword);
		return ur.searchForAdmin(
				role, status, provider, localOnly, normalizeKeyword)
				.stream()
				.map(this::toListItem)
				.toList();
	}
	
	public AdminUserStats getStats() {
		return new AdminUserStats(
				ur.count(),
				ur.countByStatus(UserStatus.ACTIVE),
				ur.countByStatus(UserStatus.DORMANT),
				ur.countByStatus(UserStatus.SUSPENDED),
				ur.countByStatus(UserStatus.WITHDRAWN)
		);
	}
	
	@Transactional
	public void suspendUser(
			Long userId,
			Long adminId
	) {
		suspendUser(
				userId,
				adminId,
				null
		);
	}
	
	@Transactional
	public void suspendUser(
			Long userId,
			Long adminId,
			String ipAddress
	) {
		User admin = requireAdmin(adminId);
		User target = requireUser(userId);
		
		if (target.getId().equals(admin.getId())) {
			throw new IllegalStateException(
					"admin.error.user.cannotSuspendSelf"
			);
		}
		
		if (target.getRole() == Role.ADMIN) {
			throw new IllegalStateException(
					"admin.error.user.cannotSuspendAdmin"
			);
		}
		
		if (target.getStatus() == UserStatus.WITHDRAWN) {
			throw new IllegalStateException(
					"admin.error.user.cannotSuspendWithdrawn"
			);
		}
		
		if (target.getStatus() == UserStatus.SUSPENDED) {
			throw new IllegalStateException(
					"admin.error.user.alreadySuspended"
			);
		}
		
		target.suspend();
		
		actionLogService.recordAction(
				adminId,
				suspendAction(target),
				accountTargetType(target),
				target.getId(),
				target.getNickname() + " 계정 정지",
				ipAddress
		);
	}
	
	@Transactional
	public void reinstateUser(
			Long userId,
			Long adminId
	) {
		reinstateUser(
				userId,
				adminId,
				null
		);
	}
	
	@Transactional
	public void reinstateUser(
			Long userId,
			Long adminId,
			String ipAddress
	) {
		User admin = requireAdmin(adminId);
		User target = requireUser(userId);
		
		if (target.getId().equals(admin.getId())) {
			throw new IllegalStateException(
					"admin.error.user.cannotChangeSelf"
			);
		}
		
		if (target.getRole() == Role.ADMIN) {
			throw new IllegalStateException(
					"admin.error.user.cannotChangeAdmin"
			);
		}
		
		if (target.getStatus() != UserStatus.SUSPENDED) {
			throw new IllegalStateException(
					"admin.error.user.notSuspended"
			);
		}
		
		target.reinstate();
		
		actionLogService.recordAction(
				adminId,
				reinstateAction(target),
				accountTargetType(target),
				target.getId(),
				target.getNickname() + " 계정 정지 해제",
				ipAddress
		);
	}
	
	private AdminUserListItemResponse toListItem(User user) {
		AuthProvider provider = user.getProvider();
		return new AdminUserListItemResponse(
				user.getId(),
				user.getUsername(),
				user.getNickname(),
				user.getEmail(),
				user.getRole().name(),
				roleLabel(user.getRole()),
				user.getStatus().name(),
				statusLabel(user.getStatus()),
				provider == null ? "LOCAL" : provider.name(),
				providerLabel(provider),
				user.getAgency() == null ? null : user.getAgency().getId(),
				user.getAgency() == null ? null : user.getAgency().getName(),
				user.getEmailVerifiedAt() != null,
				user.getCreatedAt(),
				user.getLastLoginAt()
		);
	}
	
	private AdminActionType suspendAction(
			User target
	) {
		if (target.getRole() == Role.ARTIST) {
			return AdminActionType.ARTIST_SUSPEND;
		}
		
		return AdminActionType.USER_SUSPEND;
	}
	
	private AdminActionType reinstateAction(
			User target
	) {
		if (target.getRole() == Role.ARTIST) {
			return AdminActionType.ARTIST_REINSTATE;
		}
		
		return AdminActionType.USER_REINSTATE;
	}
	
	private AdminTargetType accountTargetType(
			User target
	) {
		if (target.getRole() == Role.ARTIST) {
			return AdminTargetType.ARTIST;
		}
		
		return AdminTargetType.USER;
	}
	
	private User requireAdmin(Long adminId) {
		User admin = requireUser(adminId);
		if (admin.getRole() != Role.ADMIN) {
			throw new IllegalStateException("error.admin.adminOnly");
		}
		return admin;
	}
	
	private User requireUser(Long userId) {
		return ur.findById(userId).orElseThrow(() ->
				new IllegalArgumentException("error.community.userNotFound"));
	}
	
	private String normalizeKeyword(String keyword) {
		if (keyword == null || keyword.isBlank()) {
			return null;
		}
		return keyword.trim();
	}
	
	// 라벨에는 메시지 키를 담고 화면에서 번역한다.
	private String roleLabel(Role role) {
		return "admin.users.role." + role.name();
	}
	
	private String statusLabel(UserStatus status) {
		return "admin.userStatus." + status.name();
	}
	
	private String providerLabel(AuthProvider provider) {
		if (provider == null) {
			return "admin.users.provider.LOCAL";
		}
		return "admin.users.provider." + provider.name();
	}
	
	public record AdminUserStats(
			long totalCount,
			long activeCount,
			long dormantCount,
			long suspendedCount,
			long withdrawnCount
	) {
	
	}
}
