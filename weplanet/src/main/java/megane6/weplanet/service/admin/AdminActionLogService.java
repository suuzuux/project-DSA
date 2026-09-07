package megane6.weplanet.service.admin;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.dto.admin.AdminActionLogResponse;
import megane6.weplanet.domain.entity.AdminActionLog;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.AdminActionType;
import megane6.weplanet.domain.entity.enumfolder.AdminTargetType;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.repository.AdminActionLogRepository;
import megane6.weplanet.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminActionLogService {
	
	private static final int PAGE_SIZE = 20;
	
	private final AdminActionLogRepository logRepository;
	private final UserRepository ur;
	
	// 관리자 조치 로그 저장
	@Transactional
	public void recordAction(
			Long actorId,
			AdminActionType action,
			AdminTargetType targetType,
			Long targetId,
			String reason,
			String ipAddress
	) {
		User actor = requireAdmin(actorId);
		AdminActionLog adminLog = AdminActionLog.create(
				actor,
				action,
				targetType,
				targetId,
				reason,
				ipAddress
		);
		logRepository.save(adminLog);
	}
	
	// 관리자 로그 검색
	public Page<AdminActionLogResponse> getLogs(
			AdminActionType action,
			AdminTargetType targetType,
			Long actorId,
			Long targetId,
			LocalDate fromDate,
			LocalDate toDate,
			String keyword,
			int page
	) {
		validateDateRange(fromDate, toDate);
		LocalDateTime fromDateTime = fromDate == null ? null : fromDate.atStartOfDay();
		
		// 종료일 다음날 00:00 미만으로 조회하여 종료일 하루 전체 포함
		LocalDateTime toDateTime = toDate == null ? null : toDate.plusDays(1).atStartOfDay();
		
		String normalizedKeyword = normalizeKeyword(keyword);
		int safePage = Math.max(page, 0);
		
		return logRepository.searchForAdmin(
				action,
				targetType,
				actorId,
				targetId,
				fromDateTime,
				toDateTime,
				normalizedKeyword,
				PageRequest.of(safePage, PAGE_SIZE)
		).map(this::toResponse);
	}
	
	// 상단 통계 카드
	public AdminActionLogStats getStats() {
		LocalDateTime todayStart = LocalDate.now().atStartOfDay();
		long accountActionCount = logRepository.countByTargetType(AdminTargetType.USER)
				+ logRepository.countByTargetType(AdminTargetType.ARTIST);
		
		return new AdminActionLogStats(
				logRepository.count(),
				logRepository.countByCreatedAtGreaterThanEqual(todayStart),
				accountActionCount,
				logRepository.countByTargetType(AdminTargetType.AGENCY_PERMISSION),
				logRepository.countByTargetType(AdminTargetType.PROJECT),
				logRepository.countByTargetType(AdminTargetType.REPORT)
		);
	}
	
	// 관리자 선택 필터에 표시할 목록
	public List<AdminActorOption> getAdminActors() {
		return ur.findByRole(Role.ADMIN)
				.stream()
				.sorted(Comparator.comparing(User::getNickname,
											 String.CASE_INSENSITIVE_ORDER
				))
				.map(user -> new AdminActorOption(
						user.getId(),
						user.getUsername(),
						user.getNickname()
				))
				.toList();
	}
	
	private AdminActionLogResponse toResponse(AdminActionLog adminLog) {
		User actor = adminLog.getActor();
		return new AdminActionLogResponse(
				adminLog.getId(),
				
				actor.getId(),
				actor.getUsername(),
				actor.getNickname(),
				
				adminLog.getAction().name(),
				adminLog.getAction().getLabel(),
				
				adminLog.getTargetType().name(),
				adminLog.getTargetType().getLabel(),
				adminLog.getTargetId(),
				
				adminLog.getReason(),
				adminLog.getIpAddress(),
				
				adminLog.getCreatedAt()
		);
	}
	
	private User requireAdmin(Long actorId) {
		User actor = ur.findById(actorId).orElseThrow(() ->
				new IllegalArgumentException("관리자 계정을 찾을 수 없습니다."));
		if (actor.getRole() != Role.ADMIN) {
			throw new IllegalArgumentException("관리자 계정만 조치 로그를 생성할 수 있습니다.");
		}
		return actor;
	}
	
	private void validateDateRange(LocalDate fromDate, LocalDate toDate) {
		if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
			throw new IllegalArgumentException("시작일은 종료일보다 늦을 수 없습니다.");
		}
	}
	
	private String normalizeKeyword(String keyword) {
		if (keyword == null || keyword.isBlank()) {
			return null;
		}
		return keyword.trim();
	}
	
	public record AdminActionLogStats(
			long totalCount,
			long todayCount,
			long accountActionCount,
			long agencyActionCount,
			long projectActionCount,
			long reportActionCount
	) {
	}
	
	public record AdminActorOption(
			Long id,
			String username,
			String nickname
	) {
	}
}
