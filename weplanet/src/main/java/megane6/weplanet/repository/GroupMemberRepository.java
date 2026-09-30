package megane6.weplanet.repository;

import megane6.weplanet.domain.entity.GroupMember;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {
	
	// 프로필 선택 화면: 그룹의 활동 중인 멤버 목록 (등록 순)
	// 화면에서 멤버 이름을 바로 쓰므로 member(User)를 같이 가져온다.
	@EntityGraph(attributePaths = "member")
	List<GroupMember> findByGroupIdAndLeftAtIsNullOrderByIdAsc(Long groupId);
	
	// 관리자 아티스트 목록: 여러 그룹의 활동 중인 멤버를 한 번에 가져온다 (그룹마다 조회하면 N+1)
	@EntityGraph(attributePaths = "member")
	List<GroupMember> findByGroupIdInAndLeftAtIsNullOrderByIdAsc(Collection<Long> groupIds);

	// 그룹 로그인 직후: 멤버가 있는 그룹이면 프로필 선택으로, 없으면(솔로) 바로 커뮤니티로
	boolean existsByGroupIdAndLeftAtIsNull(Long groupId);
	
	// 프로필 선택 시: 고른 멤버가 정말 이 그룹의 활동 중인 멤버인지 확인
	Optional<GroupMember> findByGroupIdAndMember_IdAndLeftAtIsNull(Long groupId, Long memberId);
	
	// 멤버로 로그인한 뒤: "이 멤버의 커뮤니티(그룹)는 어디인가" (6단계 판정 헬퍼)
	Optional<GroupMember> findByMember_IdAndLeftAtIsNull(Long memberId);
	
	// 멤버 추가/탈퇴 후 탐색용 인원수(artist_group_profiles.member_count)를 맞출 때
	long countByGroupIdAndLeftAtIsNull(Long groupId);

	// 멤버 추가 시: 같은 그룹 안에 같은 이름의 활동 멤버가 있는지 (프로필 선택 화면에서 구분이 안 되므로)
	boolean existsByGroupIdAndLeftAtIsNullAndMember_Nickname(Long groupId, String nickname);
}