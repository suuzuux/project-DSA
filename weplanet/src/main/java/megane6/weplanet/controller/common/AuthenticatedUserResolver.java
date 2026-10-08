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

	// 글쓰기/댓글/좋아요처럼 "누가 했는지"가 실제로 중요한 동작에서 씀 - testUserId 폴백을 쓰지 않고
	// 진짜 로그인 여부만 확인함. 비로그인이면 예외를 던져서 GlobalExceptionHandler가 /login으로 보내줌
	public User requireAuthenticated(AuthenticatedUser principal) {
		if (principal == null) {
			throw new AuthenticationRequiredException();
		}
		return getAuthenticatedUserOrThrow(principal.getId());
	}
	
	// 로그인한 사람이 아티스트 쪽 계정(솔로/그룹 + 그룹 멤버)인지 - "Hide from Artists" 필터링(36번)처럼
	// 여러 컨트롤러에서 공통으로 필요한 판단이라 여기에 모아둠
	public boolean isArtist(AuthenticatedUser principal) {
		if (principal == null) {
			return false;
		}
		return getAuthenticatedUserOrThrow(principal.getId()).isArtistSide();
	}

	// 계정이 DB에서 지워졌는데 세션은 로그인 상태로 남은 경우(관리자가 삭제한 경우 등)를 구분해서 던진다.
	// GlobalExceptionHandler가 이 예외를 받으면 세션을 정리해서, "홈으로"를 눌러도 같은 오류가 반복되지 않는다.
	private User getAuthenticatedUserOrThrow(Long userId) {
		User user = userRepository.findOneById(userId)
				.orElseThrow(() -> new StaleSessionException("유저(id=" + userId + ")를 찾을 수 없습니다."));
		// 로그인해 있는 동안 관리자가 정지했거나 탈퇴 처리된 계정이면 세션을 정리한다 (남은 세션으로 글쓰기·결제 등을 막음).
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
