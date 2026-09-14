package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.AdminActionLog;
import megane6.weplanet.domain.entity.enumfolder.ReportReason;
import megane6.weplanet.domain.entity.enumfolder.ReportStatus;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.domain.entity.enumfolder.UserStatus;
import megane6.weplanet.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 최고관리자 대시보드 통계
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardService {
	
	private static final int SIGNUP_TREND_DAYS = 30;
	private static final double CHART_WIDTH = 320.0;
	private static final double CHART_TOP = 10.0;
	private static final double CHART_BOTTOM = 110.0;
	
	private static final DateTimeFormatter AXIS_DATE_FORMATTER =
			DateTimeFormatter.ofPattern("MM-dd");
	
	private final UserRepository userRepository;
	private final ReportRepository reportRepository;
	private final CommentReportRepository commentReportRepository;
	private final AgencyProfileRepository agencyProfileRepository;
	private final AdminActionLogRepository adminActionLogRepository;
	private final AdminReportService adminReportService;
	
	public DashboardStatus getStatus() {
		long totalUsers = userRepository.count();
		
		// 역할별 회원 수
		long fanCount = userRepository.countByRole(Role.FAN);
		long artistCount = userRepository.countByRole(Role.ARTIST);
		long agencyCount = userRepository.countByRole(Role.AGENCY);
		long adminCount = userRepository.countByRole(Role.ADMIN);
		
		// 활성 상태인 아티스트 계정을 하나의 활성 커뮤니티로 계산
		long activeCommunityCount = userRepository.countByRoleAndStatus(
				Role.ARTIST,
				UserStatus.ACTIVE
		);
		
		// 아직 관리자가 승인하지 않은 소속사 권한
		long pendingAgencyApprovalCount =
				agencyProfileRepository.countByApprovedAtIsNull();
		
		// 게시글 신고와 댓글 신고 중 아직 처리하지 않은 신고 수
		long pendingReportCount =
				reportRepository.countByStatus(ReportStatus.PENDING)
						+ commentReportRepository.countByStatus(
						ReportStatus.PENDING
				);
		
		// 오늘 처리된 신고 수
		LocalDateTime todayStart = LocalDate.now().atStartOfDay();
		
		long resolvedTodayCount =
				reportRepository.countByResolvedAtAfter(todayStart)
						+ commentReportRepository.countByResolvedAtAfter(
						todayStart
				);
		
		// 계정 상태별 회원 수
		long activeUserCount =
				userRepository.countByStatus(UserStatus.ACTIVE);
		
		long dormantUserCount =
				userRepository.countByStatus(UserStatus.DORMANT);
		
		long suspendedUserCount =
				userRepository.countByStatus(UserStatus.SUSPENDED);
		
		long withdrawnUserCount =
				userRepository.countByStatus(UserStatus.WITHDRAWN);
		
		// 최근 30일 가입자 추이
		SignupTrend signupTrend = buildSignupTrend();
		
		// 욕설·혐오 또는 음란물 사유의 처리 대기 신고 상위 3건
		List<SeverePendingReport> severePendingReports =
				adminReportService
						.listAll(
								null,
								ReportStatus.PENDING,
								null,
								null,
								0,
								Integer.MAX_VALUE
						)
						.content()
						.stream()
						.filter(this::isSevereReport)
						.sorted(
								Comparator
										.comparingLong(
												AdminReportService.ReportItem::reportCount
										)
										.reversed()
										.thenComparing(
												AdminReportService.ReportItem::latestReportedAt,
												Comparator.reverseOrder()
										)
						)
						.limit(3)
						.map(this::toSeverePendingReport)
						.toList();
		
		// 최근 관리자 활동 5건
		List<RecentAdminAction> recentAdminActions =
				adminActionLogRepository
						.findTop5ByOrderByCreatedAtDescIdDesc()
						.stream()
						.map(this::toRecentAdminAction)
						.toList();
		
		return new DashboardStatus(
				totalUsers,
				fanCount,
				artistCount,
				agencyCount,
				adminCount,
				activeCommunityCount,
				pendingAgencyApprovalCount,
				pendingReportCount,
				resolvedTodayCount,
				activeUserCount,
				dormantUserCount,
				suspendedUserCount,
				withdrawnUserCount,
				signupTrend,
				severePendingReports,
				recentAdminActions
		);
	}
	
	private SignupTrend buildSignupTrend() {
		LocalDate endDateExclusive =
				LocalDate.now().plusDays(1);
		
		LocalDate startDate =
				endDateExclusive.minusDays(SIGNUP_TREND_DAYS);
		
		List<UserRepository.DailySignupCount> rows =
				userRepository.countDailySignupsBetween(
						startDate.atStartOfDay(),
						endDateExclusive.atStartOfDay()
				);
		
		Map<LocalDate, Long> signupCountByDate =
				new HashMap<>();
		
		for (UserRepository.DailySignupCount row : rows) {
			if (row.getSignupDate() == null) {
				continue;
			}
			
			LocalDate signupDate =
					LocalDate.parse(row.getSignupDate());
			
			long signupCount =
					row.getSignupCount() == null
							? 0L
							: row.getSignupCount();
			
			signupCountByDate.put(
					signupDate,
					signupCount
			);
		}
		
		List<Long> dailySignupCounts =
				new ArrayList<>();
		
		for (int dayOffset = 0;
			 dayOffset < SIGNUP_TREND_DAYS;
			 dayOffset++) {
			
			LocalDate date =
					startDate.plusDays(dayOffset);
			
			dailySignupCounts.add(
					signupCountByDate.getOrDefault(
							date,
							0L
					)
			);
		}
		
		long totalCount =
				dailySignupCounts
						.stream()
						.mapToLong(Long::longValue)
						.sum();
		
		long peakCount =
				dailySignupCounts
						.stream()
						.mapToLong(Long::longValue)
						.max()
						.orElse(0L);
		
		StringBuilder chartPoints =
				new StringBuilder();
		
		for (int index = 0;
			 index < dailySignupCounts.size();
			 index++) {
			
			long signupCount =
					dailySignupCounts.get(index);
			
			double x =
					CHART_WIDTH
							* index
							/ (SIGNUP_TREND_DAYS - 1);
			
			double y;
			
			if (peakCount == 0L) {
				y = CHART_BOTTOM;
			} else {
				double ratio =
						(double) signupCount / peakCount;
				
				y = CHART_BOTTOM
						- ratio
						* (CHART_BOTTOM - CHART_TOP);
			}
			
			if (chartPoints.length() > 0) {
				chartPoints.append(' ');
			}
			
			chartPoints.append(
					String.format(
							Locale.US,
							"%.1f,%.1f",
							x,
							y
					)
			);
		}
		
		List<String> axisLabels = List.of(
				startDate.format(AXIS_DATE_FORMATTER),
				startDate.plusDays(7)
						.format(AXIS_DATE_FORMATTER),
				startDate.plusDays(14)
						.format(AXIS_DATE_FORMATTER),
				startDate.plusDays(21)
						.format(AXIS_DATE_FORMATTER),
				startDate.plusDays(29)
						.format(AXIS_DATE_FORMATTER)
		);
		
		return new SignupTrend(
				chartPoints.toString(),
				axisLabels,
				totalCount,
				peakCount
		);
	}
	
	private boolean isSevereReport(
			AdminReportService.ReportItem report
	) {
		return report.latestReason() == ReportReason.ABUSE
				|| report.latestReason() == ReportReason.SEXUAL;
	}
	
	private SeverePendingReport toSeverePendingReport(
			AdminReportService.ReportItem report
	) {
		String reasonLabel =
				switch (report.latestReason()) {
					case ABUSE -> "욕설 · 혐오";
					case SEXUAL -> "음란물";
					default -> report.latestReason().name();
				};
		
		String targetTypeLabel =
				report.targetType()
						== AdminReportService.TargetType.POST
						? "게시글"
						: "댓글";
		
		String targetSummary =
				report.targetTitle() == null
						|| report.targetTitle().isBlank()
						? targetTypeLabel + " #" + report.targetId()
						: report.targetTitle();
		
		return new SeverePendingReport(
				reasonLabel,
				targetTypeLabel,
				targetSummary,
				report.authorNickname(),
				report.reportCount()
		);
	}
	
	private RecentAdminAction toRecentAdminAction(
			AdminActionLog adminLog
	) {
		return new RecentAdminAction(
				adminLog.getActor().getUsername(),
				adminLog.getActor().getNickname(),
				adminLog.getAction().getLabel(),
				targetSummary(adminLog),
				adminLog.getCreatedAt()
		);
	}
	
	private String targetSummary(
			AdminActionLog adminLog
	) {
		if (adminLog.getReason() != null
				&& !adminLog.getReason().isBlank()) {
			
			return adminLog.getReason();
		}
		
		return adminLog.getTargetType().getLabel()
				+ " #"
				+ adminLog.getTargetId();
	}
	
	public record DashboardStatus(
			long totalUsers,
			long fanCount,
			long artistCount,
			long agencyCount,
			long adminCount,
			long activeCommunityCount,
			long pendingAgencyApprovalCount,
			long pendingReportCount,
			long resolvedTodayCount,
			long activeUserCount,
			long dormantUserCount,
			long suspendedUserCount,
			long withdrawnUserCount,
			SignupTrend signupTrend,
			List<SeverePendingReport> severePendingReports,
			List<RecentAdminAction> recentAdminActions
	) {
	}
	
	public record SignupTrend(
			String chartPoints,
			List<String> axisLabels,
			long totalCount,
			long peakCount
	) {
	}
	
	public record SeverePendingReport(
			String reasonLabel,
			String targetTypeLabel,
			String targetSummary,
			String authorNickname,
			long reportCount
	) {
	}
	
	public record RecentAdminAction(
			String actorUsername,
			String actorNickname,
			String actionLabel,
			String targetSummary,
			LocalDateTime createdAt
	) {
	}
}