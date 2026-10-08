package megane6.weplanet.repository.artist;

import megane6.weplanet.domain.entity.GroupMember;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {
	
	// 프로필 선택 화면: 그룹의 활동 멤버 목록 (등록 순)
	@EntityGraph(attributePaths = "member")
	List<GroupMember> findByGroupIdAndLeftAtIsNullOrderByIdAsc(Long groupId);
	
	// 여러 그룹의 활동 멤버를 한 번에 조회 (N+1 방지)
	@EntityGraph(attributePaths = "member")
	List<GroupMember> findByGroupIdInAndLeftAtIsNullOrderByIdAsc(Collection<Long> groupIds);

	// 멤버가 있는 그룹이면 프로필 선택으로, 없으면 바로 커뮤니티로
	boolean existsByGroupIdAndLeftAtIsNull(Long groupId);
	
	// 고른 멤버가 이 그룹의 활동 멤버인지 확인
	Optional<GroupMember> findByGroupIdAndMember_IdAndLeftAtIsNull(Long groupId, Long memberId);
	
	// 멤버의 소속 그룹 조회
	Optional<GroupMember> findByMember_IdAndLeftAtIsNull(Long memberId);
	
	// 탐색용 인원수 동기화
	long countByGroupIdAndLeftAtIsNull(Long groupId);

	// 같은 그룹에 같은 이름의 활동 멤버가 있는지
	boolean existsByGroupIdAndLeftAtIsNullAndMember_Nickname(Long groupId, String nickname);
}