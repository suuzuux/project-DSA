package megane6.weplanet.controller;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.User;
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

	private User getUserOrThrow(Long userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new IllegalArgumentException("유저(id=" + userId + ")를 찾을 수 없습니다."));
	}
}
