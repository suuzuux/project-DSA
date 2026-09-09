package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.enumfolder.ReportStatus;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.domain.entity.enumfolder.UserStatus;
import megane6.weplanet.repository.CommentReportRepository;
import megane6.weplanet.repository.ReportRepository;
import megane6.weplanet.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 최고관리자 대시보드 통계
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardService {
	private final UserRepository ur;
	private final ReportRepository rr;
	private final CommentReportRepository crr;
	
	public DashboardStatus getStatus() {
		long totalUsers = ur.count();
		long artistCount = ur.findByRole(Role.ARTIST).size();
		long fanCount = ur.findByRole(Role.FAN).size();
		
		// 게시글 신고 + 댓글 신고 중 아직 처리 안 된(PENDING) 것만 "미처리 신고"
		long pendingReportCount = rr.countByStatus(ReportStatus.PENDING)
				+ crr.countByStatus(ReportStatus.PENDING);
		
		// 자정 이후 기각(dismiss)된 신고 수
		// - "대상 삭제"로 처리된 건 report row 자체가 사라지기 때문에 여기 못 잡힘 (알려진 한계, AdminReportService 참고)
		LocalDateTime todayStart = LocalDate.now().atStartOfDay();
		long resolvedTodayCount = rr.countByResolvedAtAfter(todayStart)
				+ crr.countByResolvedAtAfter(todayStart);
		
		long suspendedUserCount = ur.countByStatus(UserStatus.SUSPENDED);
		
		return new DashboardStatus(
				totalUsers,
				artistCount,
				fanCount,
				pendingReportCount,
				resolvedTodayCount,
				suspendedUserCount
		);
	}
	
	public record DashboardStatus(
			long totalUsers,
			long artistCount,
			long fanCount,
			long pendingReportCount,
			long resolvedTodayCount,
			long suspendedUserCount
	) {
	
	}
}
