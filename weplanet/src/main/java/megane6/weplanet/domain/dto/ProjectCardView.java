package megane6.weplanet.domain.dto;

import megane6.weplanet.domain.entity.Project;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 프로젝트 목록에 뿌릴 카드 한 장 분량의 뷰 모델.
 * <p>
 * Project 엔티티를 화면에 그대로 넘기면 creator/reviewedBy 같은 User가 딸려가서
 * 비밀번호·실명 같은 민감한 값까지 템플릿에서 접근 가능해진다.
 * 그래서 ArtistCardView처럼 화면에 필요한 값만 뽑아서 넘긴다.
 */
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

    /**
     * @param coverStoredName 대표 이미지의 저장 파일명. 아직 없으면 null (카드에 기본 배경 표시)
     */
    public static ProjectCardView from(
            Project project,
            String coverStoredName,
            long fundedAmount,
            long participantCount
    ) {
        LocalDate startDate = project.getFundingStartAt().toLocalDate();
        LocalDate endDate = project.getFundingEndAt().toLocalDate();
        int progressPercent = project.getGoalAmount() <= 0
                ? 0
                : (int) Math.min(999, fundedAmount * 100 / project.getGoalAmount());

        return new ProjectCardView(
                project.getId(),
                project.getTitle(),
                project.getEventType().getDisplayName(),
                project.getStatus().getDisplayName(),
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

    // 마감일이 지났는지 (카드에서 D-day 대신 "마감"을 보여줄 때 사용)
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
