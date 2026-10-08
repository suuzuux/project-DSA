package megane6.weplanet.service.artist;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.Agency;
import megane6.weplanet.domain.entity.ArtistAccountProfile;
import megane6.weplanet.domain.entity.GroupMember;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.exception.LocalizedIllegalArgumentException;
import megane6.weplanet.exception.LocalizedIllegalStateException;
import megane6.weplanet.repository.agency.AgencyRepository;
import megane6.weplanet.repository.artist.ArtistAccountProfileRepository;
import megane6.weplanet.repository.artist.ArtistGroupRepository;
import megane6.weplanet.repository.artist.GroupMemberRepository;
import megane6.weplanet.repository.main.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** 소속사가 그룹에 멤버(프로필)를 추가한다 (멤버는 프로필 선택으로만 로그인해 username·email 은 시스템 값). */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArtistMemberService {
	private static final int MEMBER_NAME_MAX_LENGTH = 20;
	// 받을 수 없는 주소는 *.weplanet.local 로 만든다.
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

		// 같은 그룹 안에서만 멤버 이름 중복을 막는다.
		if (gmr.existsByGroupIdAndLeftAtIsNullAndMember_Nickname(groupId, memberName)) {
			throw new LocalizedIllegalStateException("error.groupMember.duplicateName", memberName);
		}
		
		String username = newMemberUsername(groupId);
		
		User member = User.createArtistMember(
				username,
				memberName,        // 실명 대신 활동명
				memberName,        // 커뮤니티에 작성자로 보이는 이름
				username + MEMBER_EMAIL_DOMAIN
		);
		member.assignAgency(agency);
		ur.save(member);
		
		// group_members FK 때문에 artist_profiles 를 먼저 만든다.
		aapr.save(ArtistAccountProfile.create(member, agency, memberName, null));
		
		GroupMember saved = gmr.save(GroupMember.join(groupId, member));
		
		syncMemberCount(groupId);
		
		log.info("그룹 멤버 추가: groupId={}, memberId={}, name={}", groupId, member.getId(), memberName);
		
		return saved;
	}
	
	// 멤버 탈퇴 - 행은 남기고 left_at 만 기록한다 (아티스트 권한도 사라짐).
	@Transactional
	public String removeMember(User agencyUser, Long groupId, Long memberId) {
		requireManagedGroup(agencyUser, groupId);
		
		GroupMember groupMember = requireActiveMember(groupId, memberId);
		groupMember.leave(LocalDate.now());
		
		syncMemberCount(groupId);
		
		log.info("그룹 멤버 탈퇴 처리: groupId={}, memberId={}", groupId, memberId);
		
		return groupMember.getMember().getNickname();
	}
	
	// 개인 비밀번호 초기화 (다음 프로필 선택 때 새로 설정)
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
	
	// 이 소속사가 관리하는 그룹인지 확인한다.
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
	
	// member_{그룹id}_{랜덤 8자리} (겹치면 다시 생성)
	private String newMemberUsername(Long groupId) {
		String candidate;
		
		do {
			candidate = "member_" + groupId + "_" + UUID.randomUUID().toString().substring(0, 8);
		} while (ur.existsByUsername(candidate));
	
		return candidate;
	}
	
	// 탐색 필터용 멤버 수 동기화 (0명이면 솔로 1, 변경 감지로 UPDATE).
	private void syncMemberCount(Long groupId) {
		long activeCount = gmr.countByGroupIdAndLeftAtIsNull(groupId);

		agr.findById(groupId)
				.ifPresent(group -> group.setMemberCount((int) Math.max(1, activeCount)));
	}
}
