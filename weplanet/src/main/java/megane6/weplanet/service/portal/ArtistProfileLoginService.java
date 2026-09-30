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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
	
	// AUTH-11: 개인 비밀번호를 5회 틀리면 그 프로필은 10분 동안 로그인할 수 없다.
	// 예전에는 횟수 제한이 없어서, 그룹 비밀번호를 아는 사람이 다른 멤버의 개인 비밀번호를 무한히 대입해 볼 수 있었다.
	private static final int MAX_FAILED_ATTEMPTS = 5;
	private static final long LOCK_MINUTES = 10;
	
	private final UserRepository ur;
	private final GroupMemberRepository gmr;
	private final PasswordEncoder pe;
	// memberId → 틀린 횟수/잠금 해제 시각 (서버 메모리 - 재시작하면 초기화)
	private final Map<Long, FailedAttempts> failedAttempts = new ConcurrentHashMap<>();
	
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
				.orElseThrow(() -> new IllegalArgumentException("선택한 프로필을 찾을 수 없습니다."));
		
		if (!member.isLoginable()) {
			throw new IllegalStateException("사용할 수 없는 프로필입니다. 소속사에 문의해주세요.");
		}
		
		if (password == null || password.isBlank()) {
			throw new IllegalArgumentException("개인 비밀번호를 입력해주세요.");
		}
		
		FailedAttempts attempts = failedAttempts.get(memberId);
		if (attempts != null && attempts.isLocked()) {
			throw new IllegalStateException("개인 비밀번호를 " + MAX_FAILED_ATTEMPTS + "회 잘못 입력해서 "
					+ LOCK_MINUTES + "분 동안 이 프로필로 로그인할 수 없습니다. 잠시 후 다시 시도해주세요.");
		}
		
		if (!member.hasPassword()) {
			// 처음 고른 프로필: 지금 입력한 값을 개인 비밀번호로 정한다.
			if (!PASSWORD_PATTERN.matcher(password).matches()) {
				throw new IllegalArgumentException("비밀번호는 영문/숫자 포함 8-20자로 입력해주세요.");
			}
			
			if (!password.equals(confirmPassword)) {
				throw new IllegalArgumentException("비밀번호 확인이 일치하지 않습니다.");
			}
			
			member.setInitialMemberPassword(pe.encode(password));
			log.info("멤버 개인 비밀번호 최초 설정: groupId={}, memberId={}", groupId, memberId);
		} else if (!pe.matches(password, member.getPassword())) {
			FailedAttempts updated = failedAttempts.compute(memberId, (id, current) ->
					(current == null || current.isExpiredLock()) ? FailedAttempts.first() : current.failedOnce());
			if (updated.isLocked()) {
				log.warn("멤버 개인 비밀번호 {}회 오입력으로 잠금: groupId={}, memberId={}", MAX_FAILED_ATTEMPTS, groupId, memberId);
				throw new IllegalStateException("개인 비밀번호를 " + MAX_FAILED_ATTEMPTS + "회 잘못 입력해서 "
						+ LOCK_MINUTES + "분 동안 이 프로필로 로그인할 수 없습니다. 잠시 후 다시 시도해주세요.");
			}
			throw new IllegalArgumentException("개인 비밀번호가 올바르지 않습니다. (남은 시도 "
					+ (MAX_FAILED_ATTEMPTS - updated.count()) + "회)");
		}
		
		failedAttempts.remove(memberId);
		member.recordLogin();
		
		return member;
	}
	
	// 틀린 횟수와 잠금 해제 시각. 5회째에 잠금 시각이 정해지고, 그 시각이 지나면 처음부터 다시 센다.
	private record FailedAttempts(int count, LocalDateTime lockedUntil) {
		static FailedAttempts first() {
			return new FailedAttempts(1, null);
		}
		FailedAttempts failedOnce() {
			int next = count + 1;
			return new FailedAttempts(next, next >= MAX_FAILED_ATTEMPTS ? LocalDateTime.now().plusMinutes(LOCK_MINUTES) : null);
		}
		boolean isLocked() {
			return lockedUntil != null && LocalDateTime.now().isBefore(lockedUntil);
		}
		boolean isExpiredLock() {
			return lockedUntil != null && !LocalDateTime.now().isBefore(lockedUntil);
		}
	}
	
	// 프로필 원 하나에 필요한 정보
	public record ProfileCard(Long memberId, String name, boolean passwordSet) {}
	
	// 프로필 선택 화면 전체
	public record ProfileScreen(Long groupId, String groupName, List<ProfileCard> profiles) {}
}
