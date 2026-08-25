package megane6.weplanet.controller;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.exception.AuthenticationRequiredException;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.security.AuthenticatedUser;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthenticatedUserResolver {

	private final UserRepository userRepository;

	public User resolve(AuthenticatedUser principal, Long testUserId) {
		if (principal != null) {
			return getUserOrThrow(principal.getId());
		}
		return getUserOrThrow(testUserId);
	}

	// 글쓰기/댓글/좋아요처럼 "누가 했는지"가 실제로 중요한 동작에서 씀 - testUserId 폴백을 쓰지 않고
	// 진짜 로그인 여부만 확인함. 비로그인이면 예외를 던져서 GlobalExceptionHandler가 /login으로 보내줌
	public User requireAuthenticated(AuthenticatedUser principal) {
		if (principal == null) {
			throw new AuthenticationRequiredException();
		}
		return getUserOrThrow(principal.getId());
	}

	// 로그인한 사람이 ARTIST 역할인지 - "Hide from Artists" 필터링(36번)처럼
	// 여러 컨트롤러에서 공통으로 필요한 판단이라 여기에 모아둠
	public boolean isArtist(AuthenticatedUser principal) {
		if (principal == null) {
			return false;
		}
		return getUserOrThrow(principal.getId()).getRole() == Role.ARTIST;
	}

	private User getUserOrThrow(Long userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new IllegalArgumentException("유저(id=" + userId + ")를 찾을 수 없습니다."));
	}
}
