package megane6.weplanet.domain.dto.admin;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record AdminArtistResponse(
		Long userId,
		String username,
		String nickname,
		String email,

		String stageName,
		LocalDate debutDate,
		String position,
		String profileImg,

		String userStatusName,
		String userStatusLabel,

		Long agencyId,
		String agencyName,

		String agencyStatusName,
		String agencyStatusLabel,

		boolean emailVerified,

		LocalDateTime createdAt,
		LocalDateTime lastLoginAt,

		// 그룹의 활동 중인 멤버 (솔로면 빈 목록)
		List<Member> members
) {

	public record Member(
			Long userId,
			String stageName,
			String position,
			String userStatusName,
			String userStatusLabel,
			LocalDate joinedAt
	) {
	}
}
