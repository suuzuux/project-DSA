package megane6.weplanet.service.portal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.GroupMember;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.repository.GroupMemberRepository;
import megane6.weplanet.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 프로필 선택(2단계 로그인).
 * 그룹 로그인을 통과한 사람에게 멤버 목록을 보여주고, 고른 멤버의 개인 비밀번호를 확인한다.
 * 개인 비밀번호가 아직 없는 멤버는 이 자리에서 처음 정한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArtistProfileLoginService {
	
	// 회원가입/계정 활성화와 같은 규칙
	private static final Pattern PASSWORD_PATTERN
			= Pattern.compile("^(?=.*[a-zA-Z])(?=.*[0-9]).{8,20}$");
	
	private final UserRepository ur;
	private final GroupMemberRepository gmr;
	private final PasswordEncoder pe;
	
	public ProfileScreen loadScreen(Long groupId) {
		User group = ur.findOneById(groupId)
				.filter(user -> user.getRole() == Role.ARTIST)
				.orElseThrow(() -> new IllegalStateException("그룹 정보를 찾을 수 없습니다. 다시 로그인해주세요."));
		
		List<ProfileCard> profiles = gmr.findByGroupIdAndLeftAtIsNullOrderByIdAsc(groupId).stream()
				.map(GroupMember::getMember)
				.map(member -> new ProfileCard(member.getId(), member.getNickname(), member.hasPassword()))
				.toList();
		
		return new ProfileScreen(group.getId(), group.getNickname(), profiles);
	}
	
	@Transactional
	public User authenticate(Long groupId, Long memberId, String password, String confirmPassword) {
		// 폼의 memberId는 조작될 수 있으므로, "이 그룹의 활동 중인 멤버"인지 DB로 다시 확인
		User member = gmr.findByGroupIdAndMember_IdAndLeftAtIsNull(groupId, memberId)
				.map(GroupMember::getMember)
				.orElseThrow(() -> new IllegalArgumentException("error.profileLogin.profileNotFound"));
		
		if (!member.isLoginable()) {
			throw new IllegalStateException("error.profileLogin.profileUnavailable");
		}
		
		if (password == null || password.isBlank()) {
			throw new IllegalArgumentException("error.profileLogin.passwordRequired");
		}
		
		if (!member.hasPassword()) {
			// 처음 고른 프로필: 지금 입력한 값을 개인 비밀번호로 정한다.
			if (!PASSWORD_PATTERN.matcher(password).matches()) {
				throw new IllegalArgumentException("signup.validation.passwordPattern");
			}
			
			if (!password.equals(confirmPassword)) {
				throw new IllegalArgumentException("error.password.confirmMismatch");
			}
			
			member.setInitialMemberPassword(pe.encode(password));
			log.info("멤버 개인 비밀번호 최초 설정: groupId={}, memberId={}", groupId, memberId);
		} else if (!pe.matches(password, member.getPassword())) {
			throw new IllegalArgumentException("error.profileLogin.passwordIncorrect");
		}
		
		member.recordLogin();
		
		return member;
	}
	
	// 프로필 원 하나에 필요한 정보
	public record ProfileCard(Long memberId, String name, boolean passwordSet) {}
	
	// 프로필 선택 화면 전체
	public record ProfileScreen(Long groupId, String groupName, List<ProfileCard> profiles) {}
}
