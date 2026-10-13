package megane6.weplanet.controller.common;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.exception.AuthenticationRequiredException;
import megane6.weplanet.exception.InactiveAccountSessionException;
import megane6.weplanet.exception.StaleSessionException;
import megane6.weplanet.repository.main.UserRepository;
import megane6.weplanet.security.AuthenticatedUser;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthenticatedUserResolver {

	private final UserRepository userRepository;

	public User resolve(AuthenticatedUser principal, Long testUserId) {
		if (principal != null) {
			return getAuthenticatedUserOrThrow(principal.getId());
		}
		return getUserOrThrow(testUserId);
	}

	// 실제 로그인 여부만 확인한다 (비로그인이면 예외 → /login).
	public User requireAuthenticated(AuthenticatedUser principal) {
		if (principal == null) {
			throw new AuthenticationRequiredException();
		}
		return getAuthenticatedUserOrThrow(principal.getId());
	}
	
	// 로그인 사용자가 아티스트 쪽 계정(솔로·그룹·멤버)인지 확인한다.
	public boolean isArtist(AuthenticatedUser principal) {
		if (principal == null) {
			return false;
		}
		return getAuthenticatedUserOrThrow(principal.getId()).isArtistSide();
	}

	// 계정이 삭제됐는데 세션만 남은 경우를 구분해 던진다 (세션 정리용).
	private User getAuthenticatedUserOrThrow(Long userId) {
		User user = userRepository.findOneById(userId)
				.orElseThrow(() -> new StaleSessionException("유저(id=" + userId + ")를 찾을 수 없습니다."));
		// 정지·탈퇴 처리된 계정이면 세션을 정리한다.
		if (!user.isLoginable()) {
			throw new InactiveAccountSessionException("유저(id=" + userId + ")가 " + user.getStatus() + " 상태라 세션을 정리합니다.");
		}
		return user;
	}

	private User getUserOrThrow(Long userId) {
		return userRepository.findOneById(userId)
				.orElseThrow(() -> new IllegalArgumentException("error.community.userNotFound"));
	}
}
