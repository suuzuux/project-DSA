package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.AdminUserListItemResponse;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.*;
import megane6.weplanet.repository.UserRepository;
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
			String keyword
	) {
		String normalizeKeyword = normalizeKeyword(keyword);
		return ur.searchForAdmin(
				role, status, provider, normalizeKeyword)
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
					"현재 로그인한 관리자 자신의 계정은 정지할 수 없습니다."
			);
		}
		
		if (target.getRole() == Role.ADMIN) {
			throw new IllegalStateException(
					"관리자 계정은 정지할 수 없습니다."
			);
		}
		
		if (target.getStatus() == UserStatus.WITHDRAWN) {
			throw new IllegalStateException(
					"탈퇴한 회원은 정지할 수 없습니다."
			);
		}
		
		if (target.getStatus() == UserStatus.SUSPENDED) {
			throw new IllegalStateException(
					"이미 정지된 회원입니다."
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
					"현재 로그인한 관리자 자신의 상태는 변경할 수 없습니다."
			);
		}
		
		if (target.getRole() == Role.ADMIN) {
			throw new IllegalStateException(
					"관리자 계정의 상태는 변경할 수 없습니다."
			);
		}
		
		if (target.getStatus() != UserStatus.SUSPENDED) {
			throw new IllegalStateException(
					"정지 상태인 회원만 정지를 해제할 수 있습니다."
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
				provider == null ? AuthProvider.LOCAL.name() : provider.name(),
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
			throw new IllegalStateException("관리자 권한이 필요합니다.");
		}
		return admin;
	}
	
	private User requireUser(Long userId) {
		return ur.findById(userId).orElseThrow(() ->
				new IllegalArgumentException("회원을 찾을 수 없습니다."));
	}
	
	private String normalizeKeyword(String keyword) {
		if (keyword == null || keyword.isBlank()) {
			return null;
		}
		return keyword.trim();
	}
	
	private String roleLabel(Role role) {
		return switch (role) {
			case FAN -> "팬";
			case ARTIST -> "아티스트";
			case AGENCY -> "소속사";
			case ADMIN -> "관리자";
		};
	}
	
	private String statusLabel(UserStatus status) {
		return switch (status) {
			case ACTIVE -> "활성";
			case DORMANT -> "휴면";
			case SUSPENDED -> "정지";
			case WITHDRAWN -> "탈퇴";
		};
	}
	
	private String providerLabel(AuthProvider provider) {
		if (provider == null) {
			return "일반 가입";
		}
		return switch (provider) {
			case LOCAL -> "일반 가입";
			case GOOGLE -> "Google";
			case KAKAO -> "Kakao";
			case LINE -> "LINE";
		};
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
