package megane6.weplanet.domain.event;

/**
 * "팬 게시판에 새 글이 올라왔다"는 알림. (BadgeActivityEvent 와 같은 방식)
 * PostService 는 이 이벤트만 던지고 끝낸다. 해시태그 총공 집계에 넣을지는 HashtagEventPostListener 가 판단한다.
 * → 글쓰기 코드가 총공 규칙(기간, 해시태그, 1일 3건...)을 몰라도 된다.
 *
 * @param postId 방금 저장된 게시글 id
 */
public record FanPostCreatedEvent(Long postId) {
}
