package megane6.weplanet.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import megane6.weplanet.domain.entity.enumfolder.AdminActionType;
import megane6.weplanet.domain.entity.enumfolder.AdminTargetType;
import megane6.weplanet.domain.entity.enumfolder.Role;

import java.time.LocalDateTime;

@Entity
@Table(name = "admin_action_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminActionLog {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	
	// 조치를 수행한 관리자
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "actor_id", nullable = false)
	private User actor;
	
	// 수행한 조치 종류
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 50)
	private AdminActionType action;
	
	// 조치 대상 종류
	@Enumerated(EnumType.STRING)
	@Column(name = "target_type", nullable = false, length = 30)
	private AdminTargetType targetType;
	
	// 대상 Entity의 PK
	// 대상이 회원이면 users.id, 프로젝트면 projects.id가 저장됨
	@Column(name = "target_id", nullable = false)
	private Long targetId;
	
	// 관리자 조치 사유
	@Column(columnDefinition = "TEXT")
	private String reason;
	
	// 조치를 실행한 관리자 IP
	@Column(name = "ip_address", length = 45)
	private String ipAddress;
	
	// 로그 생성 시각
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
	
	private AdminActionLog(
			User actor,
			AdminActionType action,
			AdminTargetType targetType,
			Long targetId,
			String reason,
			String ipAddress
	) {
		validateActor(actor);
		validateRequiredValues(
				action, targetType, targetId
		);
		
		this.actor = actor;
		this.action = action;
		this.targetType = targetType;
		this.targetId = targetId;
		this.reason = normalize(reason);
		this.ipAddress = normalize(ipAddress);
	}
	
	public static AdminActionLog create(
			User actor,
			AdminActionType action,
			AdminTargetType targetType,
			Long targetId,
			String reason,
			String ipAddress
	) {
		return new AdminActionLog(
				actor,
				action,
				targetType,
				targetId,
				reason,
				ipAddress
		);
	}
	
	@PrePersist
	public void prePersist() {
		if (createdAt == null) {
			createdAt = LocalDateTime.now();
		}
	}
	
	private void validateActor(User actor) {
		if (actor == null || actor.getRole() != Role.ADMIN) {
			throw new IllegalArgumentException("관리자 계정만 조치 로그를 생성할 수 있습니다.");
		}
	}
	
	private void validateRequiredValues(
			AdminActionType action,
			AdminTargetType targetType,
			Long targetId
	) {
		if (action == null) {
			throw new IllegalArgumentException("관리자 조치 종류가 필요합니다.");
		}
		if (targetType == null) {
			throw new IllegalArgumentException("관리자 조치 대상 종류가 필요합니다.");
		}
		if (targetId == null) {
			throw new IllegalArgumentException("관리자 조치 대상 ID가 필요합니다.");
		}
	}
	
	private String normalize(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}
}