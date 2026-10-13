package megane6.weplanet.domain.dto;
import megane6.weplanet.service.project.ProjectService;

import megane6.weplanet.domain.entity.Project;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** 프로젝트 카드 뷰 (엔티티의 민감 정보가 화면에 넘어가지 않게 필요한 값만 담음). */
public record ProjectCardView(
        Long id,
        String title,
        String eventTypeLabel,
        String statusLabel,
        String statusBadgeCode,
        Long goalAmount,
        LocalDate fundingStartAt,
        LocalDate fundingEndAt,
        String coverImageUrl,
        String creatorNickname,
        long remainingDays,
        Long fundedAmount,
        Long participantCount,
        int progressPercent
) {

    /** coverStoredName 은 대표 이미지 파일명 (없으면 null). */
    public static ProjectCardView from(
            Project project,
            String coverStoredName,
            long fundedAmount,
            long participantCount
    ) {
        return from(project, coverStoredName, fundedAmount, participantCount,
                project.getEventType().getDisplayName(), project.getStatus().getDisplayName());
    }

    /** 유형·상태 라벨을 번역해 받는 오버로드 (레코드라 MessageSource 를 직접 못 씀). */
    public static ProjectCardView from(
            Project project,
            String coverStoredName,
            long fundedAmount,
            long participantCount,
            String eventTypeLabel,
            String statusLabel
    ) {
        LocalDate startDate = project.getFundingStartAt().toLocalDate();
        LocalDate endDate = project.getFundingEndAt().toLocalDate();
        int progressPercent = project.getGoalAmount() <= 0
                ? 0
                : (int) Math.min(999, fundedAmount * 100 / project.getGoalAmount());

        return new ProjectCardView(
                project.getId(),
                project.getTitle(),
                eventTypeLabel,
                statusLabel,
                project.getStatus().getBadgeCode(),
                project.getGoalAmount(),
                startDate,
                endDate,
                coverStoredName == null ? null : "/uploads/" + coverStoredName,
                project.getCreator().getNickname(),
                ChronoUnit.DAYS.between(LocalDate.now(), endDate),
                fundedAmount,
                participantCount,
                progressPercent
        );
    }

    // 마감일이 지났는지
    public boolean isClosed() {
        return remainingDays < 0;
    }

    // D-3, D-DAY 처럼 표시할 문구
    public String dDayLabel() {
        if (remainingDays < 0) {
            return "마감";
        }
        return remainingDays == 0 ? "D-DAY" : "D-" + remainingDays;
    }

    public int progressBarPercent() {
        return Math.min(100, Math.max(0, progressPercent));
    }
}
