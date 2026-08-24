package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.dto.SignupRequestDto;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.util.NicknameGenerator;
import megane6.weplanet.util.NicknamePolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {
	
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final NicknameGenerator nicknameGenerator;
	
	@Transactional
	public User signup(SignupRequestDto dto) {
		if (!dto.isPasswordConfirmed()) {
			throw new IllegalArgumentException("비밀번호와 비밀번호 확인이 일치하지 않습니다.");
		}
		if (userRepository.existsByUsername(dto.getUsername())) {
			throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
		}
		if (userRepository.existsByEmail(dto.getEmail())) {
			throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
		}
		
		String nickname = resolveNickname(dto.getNickname());
		String encodedPassword = passwordEncoder.encode(dto.getPassword());
		
		User user = User.createFan(
				dto.getUsername(),
				encodedPassword,
				dto.getRealName(),
				nickname,
				dto.getEmail()
		);
		
		return userRepository.save(user);
	}
	
	private String resolveNickname(String requestedNickname) {
		if (requestedNickname == null || requestedNickname.isBlank()) {
			return nicknameGenerator.generate();
		}
		if (!NicknamePolicy.isAllowed(requestedNickname)) {
			throw new IllegalArgumentException("사용할 수 없는 닉네임 형식입니다.");
		}
		if (userRepository.existsByNickname(requestedNickname)) {
			throw new IllegalArgumentException("이미 사용 중인 닉네임입니다.");
		}
		return requestedNickname;
	}
}