package megane6.weplanet.repository;

import megane6.weplanet.domain.entity.UserFollow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// GroupFollow 통합: 사람↔사람 팔로우. 팔로우는 특정 커뮤니티(communityId)에 종속된다.
public interface UserFollowRepository extends JpaRepository<UserFollow, UserFollow.Pk> {

    boolean existsByFollowerIdAndFollowingIdAndCommunityId(Long followerId, Long followingId, Long communityId);

    void deleteByFollowerIdAndFollowingIdAndCommunityId(Long followerId, Long followingId, Long communityId);

    // 나를 팔로우하는 사람 수(팔로워 수) - 이 커뮤니티 안에서만
    long countByFollowingIdAndCommunityId(Long followingId, Long communityId);

    // 내가 팔로우하는 사람 수(팔로잉 수) - 이 커뮤니티 안에서만
    long countByFollowerIdAndCommunityId(Long followerId, Long communityId);

    // 팔로워 목록 - UserFollow에는 User 연관관계가 없어서, followerId들을 UserRepository.findAllById(...)로
    // 다시 조회해야 한다. 이 커뮤니티 안에서 맺어진 관계만.
    List<UserFollow> findByFollowingIdAndCommunityIdOrderByCreatedAtAsc(Long followingId, Long communityId);

    // 팔로잉 목록 - 이 커뮤니티 안에서 맺어진 관계만.
    List<UserFollow> findByFollowerIdAndCommunityIdOrderByCreatedAtAsc(Long followerId, Long communityId);

    // 내가(팬으로서) 팔로우 중인 전체 관계 - "내가 팔로우하는 아티스트 id 목록" 계산에 씀
    // (아티스트 팔로우는 following_id == community_id이므로, 이 조건으로 걸러내면 된다)
    List<UserFollow> findByFollowerId(Long followerId);

    // 커뮤니티 탈퇴 시, 그 커뮤니티에 종속된 팔로우 관계를 정리하기 위해 씀 (양방향 모두 확인 필요)
    void deleteByCommunityIdAndFollowerId(Long communityId, Long userId);

    void deleteByCommunityIdAndFollowingId(Long communityId, Long userId);

    // [회원탈퇴] 계정을 통째로 탈퇴 처리할 때, 커뮤니티 구분 없이 이 사람이 걸려 있는 팔로우 관계를 전부
    // 정리하기 위해 씀. 가입해뒀던 커뮤니티 안의 팔로우는 CommunityJoinService.leave()가 위 두 메서드로
    // 이미 지우지만, (1) 가입 없이 팔로우만 해둔 아티스트 팔로우, (2) 아티스트 본인은 자기 커뮤니티에
    // CommunityMember가 없어서 leave() 루프에 안 걸리는 "나를 팔로우하는 팬들" 관계는 이 두 메서드로 마저 정리한다.
    void deleteByFollowerId(Long userId);

    void deleteByFollowingId(Long userId);
}
