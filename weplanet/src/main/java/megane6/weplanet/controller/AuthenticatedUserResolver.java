package megane6.weplanet.controller;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.exception.AuthenticationRequiredException;
import megane6.weplanet.exception.StaleSessionException;
import megane6.weplanet.repository.UserRepository;
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

	// 로그인한 사람이 ARTIST 역할인지 - "Hide from Artists" 필터링(36번)처럼
	// 여러 컨트롤러에서 공통으로 필요한 판단이라 여기에 모아둠
	public boolean isArtist(AuthenticatedUser principal) {
		if (principal == null) {
			return false;
		}
		return getAuthenticatedUserOrThrow(principal.getId()).getRole() == Role.ARTIST;
	}

	// 세션에 남아있는 로그인 principal 기준 조회 - 계정이 DB에서 지워졌는데 세션엔 아직 로그인 상태로
	// 남아있는 경우(관리자가 직접 계정을 삭제한 경우 등)를 구분해서 던진다. GlobalExceptionHandler가
	// 이 예외를 받으면 세션 자체를 정리해주기 때문에, "홈으로" 버튼을 눌러도 같은 에러가 무한 반복되는 걸 막는다.
	private User getAuthenticatedUserOrThrow(Long userId) {
		return userRepository.findOneById(userId)
				.orElseThrow(() -> new StaleSessionException("유저(id=" + userId + ")를 찾을 수 없습니다."));
	}

	private User getUserOrThrow(Long userId) {
		return userRepository.findOneById(userId)
				.orElseThrow(() -> new IllegalArgumentException("유저(id=" + userId + ")를 찾을 수 없습니다."));
	}
}
