package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.Agency;
import megane6.weplanet.domain.entity.ArtistAccountProfile;
import megane6.weplanet.domain.entity.GroupMember;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.repository.AgencyRepository;
import megane6.weplanet.repository.ArtistAccountProfileRepository;
import megane6.weplanet.repository.GroupMemberRepository;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.repository.community.ArtistGroupProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
	private final ArtistGroupProfileRepository agpr;
	
	public List<GroupMember> activeMembers(Long groupId) {
		return gmr.findByGroupIdAndLeftAtIsNullOrderByIdAsc(groupId);
	}
	
	@Transactional
	public GroupMember addMember(User agencyUser, Long groupId, String rawName) {
		requireManagedGroup(agencyUser, groupId);
		Agency agency = ar.findById(agencyUser.agencyId())
				.orElseThrow(() -> new IllegalStateException("소속사 정보를 찾을 수 없습니다."));

		String memberName = requireName(rawName);

		// 팬 닉네임이나 다른 그룹 멤버와는 겹쳐도 된다(아티스트는 체크 표시로 구분).
		// 다만 같은 그룹 안에서 겹치면 프로필 선택 화면에서 누가 누군지 알 수 없으므로 막는다.
		if (gmr.existsByGroupIdAndLeftAtIsNullAndMember_Nickname(groupId, memberName)) {
			throw new IllegalStateException("이 그룹에 이미 같은 이름의 멤버가 있습니다: " + memberName);
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
	
	// 다른 소속사의 그룹에 멤버를 끼워 넣지 못하게, 이 소속사가 관리하는 ARTIST 계정인지 확인
	private User requireManagedGroup(User agencyUser, Long groupId) {
		Long agencyId = agencyUser == null ? null : agencyUser.agencyId();
		
		return ur.findOneById(groupId)
				.filter(user -> user.getRole() == Role.ARTIST)
				.filter(user -> agencyId != null && agencyId.equals(user.agencyId()))
				.orElseThrow(() -> new IllegalStateException("관리할 수 있는 아티스트가 아닙니다."));
	}
	
	private String requireName(String rawName) {
		if (rawName == null || rawName.isBlank()) {
			throw new IllegalArgumentException("멤버 이름을 입력해주세요.");
		}
		
		String name = rawName.trim();
		
		if (name.length() > MEMBER_NAME_MAX_LENGTH) {
			throw new IllegalArgumentException("멤버 이름은 " + MEMBER_NAME_MAX_LENGTH + "자 이내로 입력해주세요.");
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
	// ArtistGroupProfile 은 @Data라 setter가 있고, 트랜잭션이 끝날 때 변경 감지로 UPDATE 된다.
	private void syncMemberCount(Long groupId) {
		long activeCount = gmr.countByGroupIdAndLeftAtIsNull(groupId);
		
		agpr.findByArtistId(groupId)
				.ifPresent(profile -> profile.setMemberCount((int) Math.max(1, activeCount)));
	}
}
