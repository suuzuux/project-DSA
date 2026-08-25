package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.GroupFollow;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.repository.GroupFollowRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

// 와이어프레임 26번: About 위젯의 팔로잉/팔로우 버튼 처리
@Service
@RequiredArgsConstructor
public class FollowService {

    private final GroupFollowRepository groupFollowRepository;

    // 이미 팔로우 중이면 취소, 아니면 팔로우 - 토글 후 결과(true=팔로우됨) 반환
    @Transactional
    public boolean toggle(User fan, Long artistId) {
        boolean following = groupFollowRepository.existsByFanIdAndGroupId(fan.getId(), artistId);
        if (following) {
            groupFollowRepository.deleteByFanIdAndGroupId(fan.getId(), artistId);
            return false;
        }
        groupFollowRepository.save(GroupFollow.builder()
                .fanId(fan.getId())
                .groupId(artistId)
                .createdAt(LocalDateTime.now())
                .build());
        return true;
    }

    public boolean isFollowing(User fan, Long artistId) {
        if (fan == null) {
            return false;
        }
        return groupFollowRepository.existsByFanIdAndGroupId(fan.getId(), artistId);
    }

    // 로그인한 사람이 팔로우 중인 아티스트 id 전체 (여러 명 한 번에 팔로우 여부 체크할 때 씀)
    public Set<Long> getFollowedArtistIds(User fan) {
        if (fan == null) {
            return Set.of();
        }
        return groupFollowRepository.findByFanId(fan.getId()).stream()
                .map(GroupFollow::getGroupId)
                .collect(Collectors.toSet());
    }

    // 와이어프레임 10번(가입자수)에서도 재사용
    public long countFollowers(Long artistId) {
        return groupFollowRepository.countByGroupId(artistId);
    }
}
