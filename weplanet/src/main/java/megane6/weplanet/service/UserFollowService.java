package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.UserFollow;
import megane6.weplanet.domain.entity.community.CommunityProfile;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.domain.event.BadgeActivityEvent;
import megane6.weplanet.repository.UserFollowRepository;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.repository.community.CommunityMemberRepository;
import megane6.weplanet.service.community.CommunityJoinService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

// GroupFollow 통합: 사람과 사람(팬↔팬, 팬↔아티스트 계정) 사이의 팔로우.
// 예전엔 GroupFollow(About 위젯의 팬→아티스트 그룹 팔로우, 와이어프레임 26번)와 UserFollow(팬↔팬,
// FOLLOW-01)가 완전히 별개의 테이블/서비스였는데, 이름을 "UserFollow" 하나로 통합했다.
//
// 팔로우는 특정 커뮤니티(communityId)에 종속된다:
//   - 팬→아티스트: 그 아티스트 커뮤니티 가입 여부와 무관하게 팔로우 가능 (기존 GroupFollow 방식 유지).
//     target이 곧 그 커뮤니티 주인이라 following_id == community_id.
//   - 팬↔팬: 같은 커뮤니티(communityId) 가입자끼리만 가능 (기존 UserFollow 방식 유지), 상대가
//     콘텐츠를 숨긴 상태면 불가.
//   - 같은 두 사람이 여러 커뮤니티에 함께 가입돼 있어도 팔로우는 커뮤니티마다 별개의 관계다.
//     한쪽이 그 커뮤니티를 탈퇴하면(CommunityJoinService.leave) 이 관계도 함께 삭제된다.
@Service
@RequiredArgsConstructor
public class UserFollowService {

    private final UserFollowRepository userFollowRepository;
    private final UserRepository userRepository;
    private final CommunityMemberRepository communityMemberRepository;
    private final CommunityJoinService communityJoinService;
    private final ApplicationEventPublisher eventPublisher; // [배지] 아티스트 팔로우 활동 알림 발행용

    // 이미 팔로우 중이면 취소, 아니면 팔로우 - 토글 후 결과(true=팔로우됨) 반환.
    // communityId는 "어느 커뮤니티 화면에서 눌렀는지" - 이 팔로우가 속하게 될 커뮤니티다.
    @Transactional
    public boolean toggle(User me, Long targetUserId, Long communityId) {
        if (me.getId().equals(targetUserId)) {
            throw new IllegalStateException("본인을 팔로우할 수 없습니다.");
        }
        if (me.getRole() != Role.FAN && me.getRole() != Role.ARTIST) {
            throw new IllegalStateException("팬 또는 아티스트 계정만 팔로우할 수 있습니다.");
        }

        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        // 대상이 이 커뮤니티의 주인(아티스트 본인)인지 - 그렇다면 "팬→아티스트" 팔로우로 취급한다.
        boolean targetIsArtistOfThisCommunity = target.getRole() == Role.ARTIST && targetUserId.equals(communityId);

        if (!targetIsArtistOfThisCommunity) {
            // 팬↔팬 팔로우: 나도, 상대도 이 커뮤니티에 가입돼 있어야 한다.
            if (!communityMemberRepository.existsByFanIdAndArtistId(me.getId(), communityId)) {
                throw new IllegalStateException("이 커뮤니티에 가입해야 팔로우할 수 있습니다.");
            }
            if (!communityMemberRepository.existsByFanIdAndArtistId(targetUserId, communityId)) {
                throw new IllegalStateException("상대방이 이 커뮤니티에 가입되어 있지 않습니다.");
            }
            // 상대가 이 커뮤니티에서 콘텐츠를 숨긴 상태면 팔로우 불가
            CommunityProfile targetProfile = communityJoinService.profileOf(target, communityId);
            if (targetProfile != null && targetProfile.isContentHidden()) {
                throw new IllegalStateException("콘텐츠를 숨긴 사용자는 팔로우할 수 없습니다.");
            }
        }
        // 팬→아티스트는 가입 여부와 무관하게 팔로우 가능 (기존 GroupFollow 방식)

        boolean following = userFollowRepository.existsByFollowerIdAndFollowingIdAndCommunityId(
                me.getId(), targetUserId, communityId);
        if (following) {
            userFollowRepository.deleteByFollowerIdAndFollowingIdAndCommunityId(me.getId(), targetUserId, communityId);
            return false;
        }

        userFollowRepository.save(UserFollow.builder()
                .followerId(me.getId())
                .followingId(targetUserId)
                .communityId(communityId)
                .createdAt(LocalDateTime.now())
                .build());

        // [배지] 아티스트를 팔로우했을 때만 (팬↔팬 팔로우는 배지 대상 아님, 언팔로우는 배지 회수 안 함)
        if (targetIsArtistOfThisCommunity) {
            eventPublisher.publishEvent(new BadgeActivityEvent(
                    me.getId(), communityId, BadgeActivityEvent.Activity.ARTIST_FOLLOWED
            ));
        }
        return true;
    }

    // 이 커뮤니티 안에서, 내가 상대를 팔로우 중인지 (팬↔팬, 팬→아티스트 공용)
    public boolean isFollowing(User me, Long targetUserId, Long communityId) {
        if (me == null || targetUserId == null || communityId == null) {
            return false;
        }
        return userFollowRepository.existsByFollowerIdAndFollowingIdAndCommunityId(me.getId(), targetUserId, communityId);
    }

    // 아티스트 프로필 콘텐츠 열람 조건: "이 아티스트를 팔로우했는지" - isFollowing(me, artistId, artistId)와 동일하다.
    public boolean isFollowingArtist(User me, Long artistId) {
        return isFollowing(me, artistId, artistId);
    }

    // 로그인한 사람이 팔로우 중인 아티스트 id 전체 (여러 명 한 번에 팔로우 여부 체크할 때 씀).
    // 아티스트 팔로우는 following_id == community_id라는 특징으로 걸러낸다.
    public Set<Long> getFollowedArtistIds(User fan) {
        if (fan == null) {
            return Set.of();
        }
        return userFollowRepository.findByFollowerId(fan.getId()).stream()
                .filter(uf -> uf.getFollowingId().equals(uf.getCommunityId()))
                .map(UserFollow::getFollowingId)
                .collect(Collectors.toSet());
    }

    // 이 커뮤니티 안에서 나를 팔로우하는 사람 수
    public long countFollowers(Long userId, Long communityId) {
        return userFollowRepository.countByFollowingIdAndCommunityId(userId, communityId);
    }

    // 이 커뮤니티 안에서 내가 팔로우하는 사람 수
    public long countFollowing(Long userId, Long communityId) {
        return userFollowRepository.countByFollowerIdAndCommunityId(userId, communityId);
    }

    // 팔로워 목록 (닉네임+아바타 리스트 fragment용) - 이 커뮤니티 안에서 맺어진 관계만.
    public List<User> listFollowers(Long userId, Long communityId) {
        List<Long> orderedIds = userFollowRepository.findByFollowingIdAndCommunityIdOrderByCreatedAtAsc(userId, communityId)
                .stream().map(UserFollow::getFollowerId).toList();
        return resolveOrdered(orderedIds);
    }

    // 팔로잉 목록 - 이 커뮤니티 안에서 맺어진 관계만.
    public List<User> listFollowing(Long userId, Long communityId) {
        List<Long> orderedIds = userFollowRepository.findByFollowerIdAndCommunityIdOrderByCreatedAtAsc(userId, communityId)
                .stream().map(UserFollow::getFollowingId).toList();
        return resolveOrdered(orderedIds);
    }

    // 커뮤니티 탈퇴 시 팔로우 정리는 CommunityJoinService.leave()가 UserFollowRepository를 직접 써서 한다
    // (여기서 CommunityJoinService를 다시 호출하면 순환 의존이 생기기 때문).

    private List<User> resolveOrdered(List<Long> orderedIds) {
        if (orderedIds.isEmpty()) {
            return List.of();
        }
        Map<Long, User> usersById = new LinkedHashMap<>();
        userRepository.findAllById(orderedIds).forEach(user -> usersById.put(user.getId(), user));
        return orderedIds.stream().map(usersById::get).filter(Objects::nonNull).toList();
    }
}
