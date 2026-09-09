package megane6.weplanet.domain.dto.live;

import megane6.weplanet.domain.entity.live.LiveComment;

import java.time.format.DateTimeFormatter;

public record LiveCommentView(
		Long id,
		Long authorId,
		String authorNickname,
		String content,
		String createdAt,
		boolean fromArtist,
		boolean reportedByMe
) {
	private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

	public static LiveCommentView of(LiveComment comment, String nickname, Long artistId) {
		return of(comment, nickname, artistId, false);
	}

	public static LiveCommentView of(LiveComment comment, String nickname, Long artistId, boolean reportedByMe) {
		Long authorId = comment.getAuthor().getId();
		return new LiveCommentView(
				comment.getId(),
				authorId,
				nickname,
				comment.getContent(),
				comment.getCreatedAt() != null ? comment.getCreatedAt().format(ISO) : null,
				artistId != null && artistId.equals(authorId),
				reportedByMe
		);
	}
}
