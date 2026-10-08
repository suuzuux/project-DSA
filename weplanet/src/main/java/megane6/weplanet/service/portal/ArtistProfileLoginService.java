package megane6.weplanet.service.portal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.GroupMember;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.exception.LocalizedIllegalArgumentException;
import megane6.weplanet.exception.LocalizedIllegalStateException;
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

/** 프로필 선택(2단계 로그인) - 멤버 개인 비밀번호 확인 (없으면 처음 설정). */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArtistProfileLoginService {
	
	// 회원가입과 같은 비밀번호 규칙
	private static final Pattern PASSWORD_PATTERN
			= Pattern.compile("^(?=.*[a-zA-Z])(?=.*[0-9]).{8,20}$");
	
	// 5회 틀리면 그 프로필은 10분 잠금 (비밀번호 대입 방지)
	private static final int MAX_FAILED_ATTEMPTS = 5;
	private static final long LOCK_MINUTES = 10;
	
	private final UserRepository ur;
	private final GroupMemberRepository gmr;
	private final PasswordEncoder pe;
	// memberId → 실패 횟수·잠금 해제 시각 (메모리)
	private final Map<Long, FailedAttempts> failedAttempts = new ConcurrentHashMap<>();
	
	public ProfileScreen loadScreen(Long groupId) {
		User group = ur.findOneById(groupId)
				.filter(user -> user.getRole() == Role.ARTIST)
				.orElseThrow(() -> new IllegalStateException("error.profileLogin.groupNotFound"));
		
		List<ProfileCard> profiles = gmr.findByGroupIdAndLeftAtIsNullOrderByIdAsc(groupId).stream()
				.map(GroupMember::getMember)
				.map(member -> new ProfileCard(member.getId(), member.getNickname(), member.hasPassword()))
				.toList();
		
		return new ProfileScreen(group.getId(), group.getNickname(), profiles);
	}
	
	@Transactional
	public User authenticate(Long groupId, Long memberId, String password, String confirmPassword) {
		// 폼의 memberId 를 신뢰하지 않고 이 그룹의 활동 멤버인지 다시 확인한다.
		User member = gmr.findByGroupIdAndMember_IdAndLeftAtIsNull(groupId, memberId)
				.map(GroupMember::getMember)
				.orElseThrow(() -> new IllegalArgumentException("error.profileLogin.profileNotFound"));
		
		if (!member.isLoginable()) {
			throw new IllegalStateException("error.profileLogin.profileUnavailable");
		}
		
		if (password == null || password.isBlank()) {
			throw new IllegalArgumentException("error.profileLogin.passwordRequired");
		}
		
		FailedAttempts attempts = failedAttempts.get(memberId);
		if (attempts != null && attempts.isLocked()) {
			throw new LocalizedIllegalStateException("error.profileLogin.locked", MAX_FAILED_ATTEMPTS, LOCK_MINUTES);
		}
		
		if (!member.hasPassword()) {
			// 처음 고른 프로필은 입력값을 개인 비밀번호로 정한다.
			if (!PASSWORD_PATTERN.matcher(password).matches()) {
				throw new IllegalArgumentException("signup.validation.passwordPattern");
			}
			
			if (!password.equals(confirmPassword)) {
				throw new IllegalArgumentException("error.password.confirmMismatch");
			}
			
			member.setInitialMemberPassword(pe.encode(password));
			log.info("멤버 개인 비밀번호 최초 설정: groupId={}, memberId={}", groupId, memberId);
		} else if (!pe.matches(password, member.getPassword())) {
			FailedAttempts updated = failedAttempts.compute(memberId, (id, current) ->
					(current == null || current.isExpiredLock()) ? FailedAttempts.first() : current.failedOnce());
			if (updated.isLocked()) {
				log.warn("멤버 개인 비밀번호 {}회 오입력으로 잠금: groupId={}, memberId={}", MAX_FAILED_ATTEMPTS, groupId, memberId);
				throw new LocalizedIllegalStateException("error.profileLogin.locked", MAX_FAILED_ATTEMPTS, LOCK_MINUTES);
			}
			throw new LocalizedIllegalArgumentException("error.profileLogin.passwordIncorrectRemaining",
					MAX_FAILED_ATTEMPTS - updated.count());
		}
		
		failedAttempts.remove(memberId);
		member.recordLogin();
		
		return member;
	}
	
	// 실패 횟수와 잠금 해제 시각 (5회째 잠금)
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
	
	// 프로필 카드 정보
	public record ProfileCard(Long memberId, String name, boolean passwordSet) {}
	
	// 프로필 선택 화면 정보
	public record ProfileScreen(Long groupId, String groupName, List<ProfileCard> profiles) {}
}
