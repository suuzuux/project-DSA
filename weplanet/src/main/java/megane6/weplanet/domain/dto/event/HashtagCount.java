package megane6.weplanet.domain.dto.event;

// JPQL group by 결과 한 줄 (key 는 참여팀 id 또는 제외 사유 등).
public record HashtagCount<K>(
		K key,
		Long count
) {
}
