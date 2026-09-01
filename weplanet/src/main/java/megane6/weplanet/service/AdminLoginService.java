package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.service.email.MailSenderService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 최고관리자 로그인 2단계 인증
 * 1) 아이디/비밀번호 확인 -> 관리자 계정 이메일로 인증번호 발송
 * 2) 인증번호 확인 -> 로그인 허용
 * 인증번호만으로는 로그인 X, 2단계에서도 아이디/비밀번호 다시 확인
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminLoginService {
	private static final long EXPIRE_MINUTES = 5;
	
	private final UserRepository ur;
	private final PasswordEncoder passwordEncoder;
	private final EmailVerificationService evs;
	private final MailSenderService mss;
	
	/**
	 * 1단계 - 아이디/비밀번호를 확인하고 인증번호를 보낸다.
	 *
	 * @return 화면이 2단계에서 되돌려줘야 할 인증 키
	 */
	@Transactional
	public IssuedResult issueCode(String username, String rawPassword) {
		User admin = authenticate(username, rawPassword);
		
		EmailVerificationService.IssuedVerification issued =
				evs.issueAdminLoginVerification(admin.getId());
		
		mss.sendAdminLoginCode(
				issued.recipientEmail(),
				issued.rawCode(),
				EXPIRE_MINUTES
		);
		
		return new IssuedResult(issued.verificationKey(),
				maskEmail(issued.recipientEmail()));
	}
	
	/**
	 * 2단계 - 아이디 및 비밀번호 재확인, 인증번호 일치 시 관리자 돌려줌
	 * @param username
	 * @param rawPassword
	 * @param verificationKey
	 * @param code
	 * @return
	 */
	@Transactional(noRollbackFor = {IllegalArgumentException.class,
									IllegalStateException.class})
	public User confirmCode(
			String username,
			String rawPassword,
			String verificationKey,
			String code
	) {
		User admin = authenticate(username, rawPassword);
		EmailVerificationService.VerificationResult result =
				evs.confirmAdminLoginVerification(
						admin.getId(),
						verificationKey,
						code
				);
		if (!result.verified()) {
			throw new IllegalArgumentException("인증번호가 올바르지 않습니다. (남은 시도 "
					+ result.remainingAttempts() + "회)");
		}
		return admin;
	}
	
	// 아이디, 비밀번호, 역할, 계정상태 한 번에 확인
	private User authenticate(String username, String rawPassword) {
		User admin = ur.findByUsername(username).orElseThrow(() ->
				new IllegalArgumentException("아이디 또는 비밀번호가 올바르지 않습니다."));
		
		// 아이디, 비밀번호 중 어느 곳이 틀렸는지 알려주지 X (보안강화)
		if (!passwordEncoder.matches(rawPassword, admin.getPassword())) {
			throw new IllegalArgumentException("아이디 또는 비밀번호가 올바르지 않습니다.");
		}
		
		if (admin.getRole() != Role.ADMIN) {
			throw new IllegalArgumentException("아이디 또는 비밀번호가 올바르지 않습니다.");
		}
		
		if (!admin.isLoginable()) {
			throw new IllegalStateException("사용할 수 없는 계정입니다.");
		}
		
		return admin;
	}
	
	// 화면에 "ab****@gmail.com로 보냈습니다."라고 안내하기 위함
	private String maskEmail(String email) {
		int at = email.indexOf('@');
		if (at <= 2) {
			return "***" + email.substring(Math.max(at, 0));
		}
		return email.substring(0, 2) + "****" + email.substring(at);
	}
	
	public record IssuedResult(String verificationKey, String maskedEmail) {
	
	}
}
