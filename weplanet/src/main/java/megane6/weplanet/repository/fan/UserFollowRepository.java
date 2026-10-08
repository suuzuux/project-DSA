package megane6.weplanet.repository.fan;
import megane6.weplanet.repository.main.UserRepository;

import megane6.weplanet.domain.entity.UserFollow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// 커뮤니티별 팔로우 관계
public interface UserFollowRepository extends JpaRepository<UserFollow, UserFollow.Pk> {

    boolean existsByFollowerIdAndFollowingIdAndCommunityId(Long followerId, Long followingId, Long communityId);

    void deleteByFollowerIdAndFollowingIdAndCommunityId(Long followerId, Long followingId, Long communityId);

    // 커뮤니티 내 팔로워 수
    long countByFollowingIdAndCommunityId(Long followingId, Long communityId);

    // 커뮤니티 내 팔로잉 수
    long countByFollowerIdAndCommunityId(Long followerId, Long communityId);

    // 커뮤니티 내 팔로워 목록 (User 는 따로 조회)
    List<UserFollow> findByFollowingIdAndCommunityIdOrderByCreatedAtAsc(Long followingId, Long communityId);

    // 커뮤니티 내 팔로잉 목록
    List<UserFollow> findByFollowerIdAndCommunityIdOrderByCreatedAtAsc(Long followerId, Long communityId);

    // 내가 팔로우 중인 전체 관계 (아티스트 팔로우는 following_id == community_id)
    List<UserFollow> findByFollowerId(Long followerId);

    // 커뮤니티 탈퇴 시 그 커뮤니티의 팔로우 정리
    void deleteByCommunityIdAndFollowerId(Long communityId, Long userId);

    // 탈퇴 시 아티스트 팔로우는 남기고 나머지만 삭제
    void deleteByCommunityIdAndFollowerIdAndFollowingIdNot(Long communityId, Long followerId, Long followingId);

    void deleteByCommunityIdAndFollowingId(Long communityId, Long userId);

    // 회원 탈퇴 시 이 사람의 팔로우 관계를 모두 삭제
    void deleteByFollowerId(Long userId);

    void deleteByFollowingId(Long userId);
}
