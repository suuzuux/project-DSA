package megane6.weplanet.domain.dto.live;

import megane6.weplanet.domain.entity.live.LiveSession;

import java.time.format.DateTimeFormatter;

public record LiveStatusView(
		boolean live,
		Long sessionId,
		Long artistId,
		Long hostUserId,
		String startedAt
) {
	private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

	public static LiveStatusView offline() {
		return new LiveStatusView(false, null, null, null, null);
	}

	public static LiveStatusView from(LiveSession session) {
		if (session == null || !session.isLive()) {
			return offline();
		}
		return new LiveStatusView(
				true,
				session.getId(),
				session.getArtist().getId(),
				session.getHost().getId(),
				session.getStartedAt() != null ? session.getStartedAt().format(ISO) : null
		);
	}
}
