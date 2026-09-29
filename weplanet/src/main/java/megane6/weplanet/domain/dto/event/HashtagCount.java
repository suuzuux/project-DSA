package megane6.weplanet.domain.dto.event;

// JPQL "group by" 결과 한 줄을 받는 그릇.
// key는 참여팀 id(target.id) 또는 제외 사유(status) 등
public record HashtagCount<K>(
		K key,
		Long count
) {
}
