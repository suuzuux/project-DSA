package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.repository.CommentReportRepository;
import megane6.weplanet.repository.ReportRepository;
import megane6.weplanet.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
	
	public DashboardStats getStats() {
		long totalUsers = ur.count();
		long artistCount = ur.findByRole(Role.ARTIST).size();
		long fanCount = ur.findByRole(Role.FAN).size();
		
		// 게시글 신고 + 댓글 신고 합쳐 "미처리 신고"
		// 처리(기각/대상 삭제)되면 AdminReportService가 해당 신고 로우를 바로 지우기 때문에,
		// 테이블에 남아있는 전체 건수 = 아직 처리되지 않은 신고 건수 (자세한 처리 화면은 /admin/reports)
		long reportCount = rr.count() + crr.count();
		
		return new DashboardStats(totalUsers, artistCount, fanCount, reportCount);
	}
	
	public record DashboardStats(
			long totalUsers,
			long artistCount,
			long fanCount,
			long reportCount
	) {
	
	}
}
