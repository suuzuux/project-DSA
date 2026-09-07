package megane6.weplanet.domain.dto.live;

import megane6.weplanet.domain.entity.live.LiveComment;

import java.time.format.DateTimeFormatter;

public record LiveCommentView(
		Long id,
		Long authorId,
		String authorNickname,
		String content,
		String createdAt
) {
	private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

	public static LiveCommentView of(LiveComment comment, String nickname) {
		return new LiveCommentView(
				comment.getId(),
				comment.getAuthor().getId(),
				nickname,
				comment.getContent(),
				comment.getCreatedAt() != null ? comment.getCreatedAt().format(ISO) : null
		);
	}
}
