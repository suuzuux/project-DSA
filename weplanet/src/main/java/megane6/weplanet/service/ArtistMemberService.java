package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.Agency;
import megane6.weplanet.domain.entity.ArtistAccountProfile;
import megane6.weplanet.domain.entity.GroupMember;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.exception.LocalizedIllegalArgumentException;
import megane6.weplanet.exception.LocalizedIllegalStateException;
import megane6.weplanet.repository.AgencyRepository;
import megane6.weplanet.repository.ArtistAccountProfileRepository;
import megane6.weplanet.repository.ArtistGroupRepository;
import megane6.weplanet.repository.GroupMemberRepository;
import megane6.weplanet.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 소속사가 그룹(커뮤니티)에 멤버(프로필)를 추가한다.
 * 멤버는 아이디/비밀번호로 직접 로그인하지 않는다.
 * 그룹 로그인 -> 프로필 선택 -> 개인 비밀번호 (처음이면 설정) 순으로만 들어온다.
 * 그래서 username/email은 사람이 쓸 일이 없는 시스템용 값으로 만든다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArtistMemberService {
	private static final int MEMBER_NAME_MAX_LENGTH = 20;
	// 카카오/LINE 가입자와 같은 규칙: 실제로 받을 수 없는 주소는 *.weplanet.local로 만든다.
	private static final String MEMBER_EMAIL_DOMAIN = "@member.weplanet.local";
	
	private final UserRepository ur;
	private final AgencyRepository ar;
	private final ArtistAccountProfileRepository aapr;
	private final GroupMemberRepository gmr;
	private final ArtistGroupRepository agr;
	
	public List<GroupMember> activeMembers(Long groupId) {
		return gmr.findByGroupIdAndLeftAtIsNullOrderByIdAsc(groupId);
	}
	
	@Transactional
	public GroupMember addMember(User agencyUser, Long groupId, String rawName) {
		requireManagedGroup(agencyUser, groupId);
		Agency agency = ar.findById(agencyUser.agencyId())
				.orElseThrow(() -> new IllegalStateException("error.portalArtist.agencyNotFound"));

		String memberName = requireName(rawName);

		// 팬 닉네임이나 다른 그룹 멤버와는 겹쳐도 된다(아티스트는 체크 표시로 구분).
		// 다만 같은 그룹 안에서 겹치면 프로필 선택 화면에서 누가 누군지 알 수 없으므로 막는다.
		if (gmr.existsByGroupIdAndLeftAtIsNullAndMember_Nickname(groupId, memberName)) {
			throw new LocalizedIllegalStateException("error.groupMember.duplicateName", memberName);
		}
		
		String username = newMemberUsername(groupId);
		
		User member = User.createArtistMember(
				username,
				memberName,        // 실명칸 : 멤버도 실명 대신 활동명을 쓴다
				memberName,        // 닉네임 = 커뮤니티에 작성자로 보이는 이름
				username + MEMBER_EMAIL_DOMAIN
		);
		member.assignAgency(agency);
		ur.save(member);
		
		// group_members.artist_id 가 artist_profiles.user_id를 FK로 보므로 먼저 만든다
		aapr.save(ArtistAccountProfile.create(member, agency, memberName, null));
		
		GroupMember saved = gmr.save(GroupMember.join(groupId, member));
		
		syncMemberCount(groupId);
		
		log.info("그룹 멤버 추가: groupId={}, memberId={}, name={}", groupId, member.getId(), memberName);
		
		return saved;
	}
	
	// 멤버 탈퇴 : 행은 남기고 left_at만 기록(글 작성자 이력 보존)
	// 탈퇴 즉시 프로필 목록에서 빠지고, CommunityArtistResolver가 활동 중 소속만 보므로, 아티스트 권한도 사라짐
	@Transactional
	public String removeMember(User agencyUser, Long groupId, Long memberId) {
		requireManagedGroup(agencyUser, groupId);
		
		GroupMember groupMember = requireActiveMember(groupId, memberId);
		groupMember.leave(LocalDate.now());
		
		syncMemberCount(groupId);
		
		log.info("그룹 멤버 탈퇴 처리: groupId={}, memberId={}", groupId, memberId);
		
		return groupMember.getMember().getNickname();
	}
	
	// 개인 비밀번호 초기화: 다음 프로필 선택 때 본인이 새로 정한다.
	@Transactional
	public String resetMemberPassword(User agencyUser, Long groupId, Long memberId) {
		requireManagedGroup(agencyUser, groupId);
		
		User member = requireActiveMember(groupId, memberId).getMember();
		member.resetMemberPassword();
		
		log.info("그룹 멤버 개인 비밀번호 초기화: groupId={}, memberId={}", groupId, memberId);
		
		return member.getNickname();
	}
	
	private GroupMember requireActiveMember(Long groupId, Long memberId) {
		return gmr.findByGroupIdAndMember_IdAndLeftAtIsNull(groupId, memberId)
				.orElseThrow(() -> new IllegalStateException("error.groupMember.notActive"));
	}
	
	// 다른 소속사의 그룹에 멤버를 끼워 넣지 못하게, 이 소속사가 관리하는 ARTIST 계정인지 확인
	private User requireManagedGroup(User agencyUser, Long groupId) {
		Long agencyId = agencyUser == null ? null : agencyUser.agencyId();
		
		return ur.findOneById(groupId)
				.filter(user -> user.getRole() == Role.ARTIST)
				.filter(user -> agencyId != null && agencyId.equals(user.agencyId()))
				.orElseThrow(() -> new IllegalStateException("error.portalArtist.notManaged"));
	}
	
	private String requireName(String rawName) {
		if (rawName == null || rawName.isBlank()) {
			throw new IllegalArgumentException("error.groupMember.nameRequired");
		}
		
		String name = rawName.trim();
		
		if (name.length() > MEMBER_NAME_MAX_LENGTH) {
			throw new LocalizedIllegalArgumentException("error.groupMember.nameTooLong", MEMBER_NAME_MAX_LENGTH);
		}
		
		return name;
	}
	
	// member_{그룹id}_{랜덤 8자리}. 겹칠 확률은 낮지만 혹시 겹치면 다시 뽑는다.
	private String newMemberUsername(Long groupId) {
		String candidate;
		
		do {
			candidate = "member_" + groupId + "_" + UUID.randomUUID().toString().substring(0, 8);
		} while (ur.existsByUsername(candidate));
	
		return candidate;
	}
	
	// 커뮤니티 탐색의 "솔로/그룹" 필터가 member_count를 본다. 멤버가 0명이면 솔로(1)로 둔다.
	// ArtistGroup 은 @Data라 setter가 있고, 트랜잭션이 끝날 때 변경 감지로 UPDATE 된다.
	// (artist_groups.id == 그룹 계정 users.id 라서 groupId 로 바로 찾는다)
	private void syncMemberCount(Long groupId) {
		long activeCount = gmr.countByGroupIdAndLeftAtIsNull(groupId);

		agr.findById(groupId)
				.ifPresent(group -> group.setMemberCount((int) Math.max(1, activeCount)));
	}
}
