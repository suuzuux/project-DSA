package megane6.weplanet.domain.dto.community;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

// 커뮤니티 프로필에 표시할 가입일과 D+N 값을 한 묶음으로 전달한다.
public record CommunityJoinInfo(
		LocalDate joinedDate,
		long dayNumber
) {
	public static CommunityJoinInfo from(LocalDateTime joinedAt, LocalDate today) {
		LocalDate joinedDate = joinedAt.toLocalDate();
		long dayNumber = ChronoUnit.DAYS.between(joinedDate, today) + 1;
		return new CommunityJoinInfo(joinedDate, dayNumber);
	}
}
