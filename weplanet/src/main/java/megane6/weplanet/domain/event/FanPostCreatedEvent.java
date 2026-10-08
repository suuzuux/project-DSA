package megane6.weplanet.domain.event;
import megane6.weplanet.service.fan.PostService;

/** 팬 게시판 새 글 이벤트 (총공 집계 여부는 HashtagEventPostListener 가 판단). */
public record FanPostCreatedEvent(Long postId) {
}
