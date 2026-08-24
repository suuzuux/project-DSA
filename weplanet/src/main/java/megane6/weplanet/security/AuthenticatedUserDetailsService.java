package megane6.weplanet.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthenticatedUserDetailsService implements UserDetailsService {
	
	private final UserRepository userRepository;
	
	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		log.debug("로그인 시도: {}", username);
		
		User user = userRepository.findByUsername(username)
				.orElseThrow(() -> new UsernameNotFoundException(username + ": 없는 아이디입니다."));
		
		log.debug("조회 정보: {}", user);
		
		AuthenticatedUser authenticatedUser = AuthenticatedUser.builder()
				.id(user.getId())
				.username(user.getUsername())
				.password(user.getPassword())
				.nickname(user.getNickname())
				.enabled(user.isLoginable())
				.roleName(user.getRole().authority())
				.build();
		
		log.debug("인증 정보: {}", authenticatedUser);
		return authenticatedUser;
	}
}