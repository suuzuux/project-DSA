package megane6.weplanet.service.fan;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.UserFollow;
import megane6.weplanet.domain.entity.community.CommunityMember;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.domain.event.BadgeActivityEvent;
import megane6.weplanet.repository.fan.UserFollowRepository;
import megane6.weplanet.repository.main.UserRepository;
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

// 사람 간 팔로우 (팬→아티스트는 가입 없이, 팬↔팬은 같은 커뮤니티 가입자끼리, 커뮤니티별 관계).
@Service
@RequiredArgsConstructor
public class UserFollowService {

    private final UserFollowRepository userFollowRepository;
    private final UserRepository userRepository;
    private final CommunityMemberRepository communityMemberRepository;
    private final CommunityJoinService communityJoinService;
    private final ApplicationEventPublisher eventPublisher; // [배지] 아티스트 팔로우 이벤트 발행

    // 팔로우 토글 (true = 팔로우됨, communityId 는 누른 커뮤니티).
    @Transactional
    public boolean toggle(User me, Long targetUserId, Long communityId) {
        if (me.getId().equals(targetUserId)) {
            throw new IllegalStateException("error.follow.self");
        }

        // 취소는 조건과 상관없이 항상 허용한다.
        if (userFollowRepository.existsByFollowerIdAndFollowingIdAndCommunityId(me.getId(), targetUserId, communityId)) {
            userFollowRepository.deleteByFollowerIdAndFollowingIdAndCommunityId(me.getId(), targetUserId, communityId);
            return false;
        }

        // 새로 팔로우하는 경우의 조건
        if (!me.canParticipateInCommunity()) {
            throw new IllegalStateException("error.follow.fanOrArtistOnly");
        }

        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("error.community.userNotFound"));

        // 대상이 커뮤니티 주인 아티스트면 아티스트 팔로우로 처리한다.
        boolean targetIsArtistOfThisCommunity = target.getRole() == Role.ARTIST && targetUserId.equals(communityId);

        if (!targetIsArtistOfThisCommunity) {
            // 팬↔팬은 둘 다 이 커뮤니티에 가입해야 한다.
            if (!communityMemberRepository.existsByFanIdAndArtistId(me.getId(), communityId)) {
                throw new IllegalStateException("error.follow.joinRequired");
            }
            if (!communityMemberRepository.existsByFanIdAndArtistId(targetUserId, communityId)) {
                throw new IllegalStateException("error.follow.targetNotJoined");
            }
            // 상대가 콘텐츠를 숨겼으면 팔로우 불가
            CommunityMember targetProfile = communityJoinService.profileOf(target, communityId);
            if (targetProfile != null && targetProfile.isContentHidden()) {
                throw new IllegalStateException("error.follow.targetHidden");
            }
        }
        // 아티스트 팔로우는 가입 여부와 무관하다.

        userFollowRepository.save(UserFollow.builder()
                .followerId(me.getId())
                .followingId(targetUserId)
                .communityId(communityId)
                .createdAt(LocalDateTime.now())
                .build());

        // [배지] 아티스트 팔로우만 배지 대상 (언팔로우해도 회수 없음)
        if (targetIsArtistOfThisCommunity) {
            eventPublisher.publishEvent(new BadgeActivityEvent(
                    me.getId(), communityId, BadgeActivityEvent.Activity.ARTIST_FOLLOWED
            ));
        }
        return true;
    }

    // 이 커뮤니티에서 상대를 팔로우 중인지
    public boolean isFollowing(User me, Long targetUserId, Long communityId) {
        if (me == null || targetUserId == null || communityId == null) {
            return false;
        }
        return userFollowRepository.existsByFollowerIdAndFollowingIdAndCommunityId(me.getId(), targetUserId, communityId);
    }

    // 아티스트를 팔로우했는지
    public boolean isFollowingArtist(User me, Long artistId) {
        return isFollowing(me, artistId, artistId);
    }

    // 팔로우 중인 아티스트 id 전체 (following_id == community_id).
    public Set<Long> getFollowedArtistIds(User fan) {
        if (fan == null) {
            return Set.of();
        }
        return userFollowRepository.findByFollowerId(fan.getId()).stream()
                .filter(uf -> uf.getFollowingId().equals(uf.getCommunityId()))
                .map(UserFollow::getFollowingId)
                .collect(Collectors.toSet());
    }

    // 커뮤니티 내 팔로워 수
    public long countFollowers(Long userId, Long communityId) {
        return userFollowRepository.countByFollowingIdAndCommunityId(userId, communityId);
    }

    // 커뮤니티 내 팔로잉 수
    public long countFollowing(Long userId, Long communityId) {
        return userFollowRepository.countByFollowerIdAndCommunityId(userId, communityId);
    }

    // 커뮤니티 내 팔로워 목록
    public List<User> listFollowers(Long userId, Long communityId) {
        List<Long> orderedIds = userFollowRepository.findByFollowingIdAndCommunityIdOrderByCreatedAtAsc(userId, communityId)
                .stream().map(UserFollow::getFollowerId).toList();
        return resolveOrdered(orderedIds);
    }

    // 커뮤니티 내 팔로잉 목록
    public List<User> listFollowing(Long userId, Long communityId) {
        List<Long> orderedIds = userFollowRepository.findByFollowerIdAndCommunityIdOrderByCreatedAtAsc(userId, communityId)
                .stream().map(UserFollow::getFollowingId).toList();
        return resolveOrdered(orderedIds);
    }

    // 탈퇴 시 팔로우 정리는 순환 의존을 피하려고 CommunityJoinService 가 직접 한다.

    private List<User> resolveOrdered(List<Long> orderedIds) {
        if (orderedIds.isEmpty()) {
            return List.of();
        }
        Map<Long, User> usersById = new LinkedHashMap<>();
        userRepository.findAllById(orderedIds).forEach(user -> usersById.put(user.getId(), user));
        return orderedIds.stream().map(usersById::get).filter(Objects::nonNull).toList();
    }
}
