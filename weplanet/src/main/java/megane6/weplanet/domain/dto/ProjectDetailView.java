package megane6.weplanet.domain.dto;

import megane6.weplanet.domain.entity.Project;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public record ProjectDetailView(
		Long id,
		String title,
		String description,
		String eventTypeLabel,
		String statusLabel,
		String statusBadgeCode,
		Long goalAmount,
		LocalDate fundingStartAt,
		LocalDate fundingEndAt,
		String coverImageUrl,
		String creatorNickname,
		LocalDateTime createdAt,
		String rejectionReason,
		long remainingDays
) {
	public static ProjectDetailView from(Project project, String coverStoredName) {
		LocalDate endDate = project.getFundingEndAt().toLocalDate();
		return new ProjectDetailView(
				project.getId(),
				project.getTitle(),
				project.getDescription(),
				project.getEventType().getDisplayName(),
				project.getStatus().getDisplayName(),
				project.getStatus().getBadgeCode(),
				project.getGoalAmount(),
				project.getFundingStartAt().toLocalDate(),
				endDate,
				coverStoredName == null ? null : "/uploads/" + coverStoredName,
				project.getCreator().getNickname(),
				project.getCreatedAt(),
				project.getRejectionReason(),
				ChronoUnit.DAYS.between(LocalDate.now(), endDate)
		);
	}

	public boolean isClosed() {
		return remainingDays < 0;
	}

	public String dDayLabel() {
		if (remainingDays < 0) {
			return "마감";
		}
		return remainingDays == 0 ? "D-DAY" : "D-" + remainingDays;
	}

	// 반려 시유가 있을 때만 화면에 안내 박스를 띄우기 위함
	public boolean hasRejectionReason() {
		return rejectionReason != null && !rejectionReason.isBlank();
	}
}
