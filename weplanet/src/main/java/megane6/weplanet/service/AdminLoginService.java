package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.service.email.MailSenderService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** 관리자 2단계 인증 - 비밀번호 확인 후 이메일 인증번호 발송, 인증번호 확인 시 다시 비밀번호까지 확인. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminLoginService {
	// 메일에 안내할 유효시간 (실제 만료 설정과 같은 값)
	private static final long EXPIRE_MINUTES = EmailVerificationService.ADMIN_EXPIRATION_MINUTES;
	
	private final UserRepository ur;
	private final PasswordEncoder passwordEncoder;
	private final EmailVerificationService evs;
	private final MailSenderService mss;
	private final megane6.weplanet.i18n.Messages messages;
	
	/** 1단계 - 아이디·비밀번호 확인 후 인증번호를 보낸다 (화면에 돌려줄 인증 키 반환). */
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
		
		// 이메일은 숨기고 인증 키와 만료 시각만 내려보낸다.
		return new IssuedResult(issued.verificationKey(), issued.expiresAt());
	}

	/** 인증번호만 미리 확인한다 (실패 횟수 증가가 롤백되지 않게 noRollbackFor). */
	@Transactional(noRollbackFor = {IllegalArgumentException.class, IllegalStateException.class})
	public void verifyOnly(String username, String rawPassword, String verificationKey, String code) {
		User admin = authenticate(username, rawPassword);

		EmailVerificationService.VerificationResult result =
				evs.confirmAdminLoginVerification(admin.getId(), verificationKey, code);

		if (!result.verified()) {
			throw new IllegalArgumentException(
					messages.get("admin.login.error.invalidCode", result.remainingAttempts())
			);
		}
	}
	
	/** 2단계 - 아이디·비밀번호 재확인과 인증번호 확인 후 인증 기록을 소모하고 관리자를 돌려준다. */
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
			throw new IllegalArgumentException(messages.get("admin.login.error.invalidCode", result.remainingAttempts()
					));
		}

		// 사용한 인증번호는 다시 쓸 수 없게 한다.
		evs.consumeAdminLoginVerification(admin.getId(), verificationKey);
		admin.recordLogin();
		return admin;
	}
	
	// 아이디·비밀번호·역할·상태를 한 번에 확인
	private User authenticate(String username, String rawPassword) {
		String normalizedUsername = username == null ? "" : username.trim();
		User admin = ur.findByUsername(normalizedUsername).orElseThrow(() ->
				new IllegalArgumentException("admin.login.error.badCredentials"));
		
		// 아이디와 비밀번호 중 무엇이 틀렸는지 알려주지 않는다.
		if (!passwordEncoder.matches(rawPassword, admin.getPassword())) {
			throw new IllegalArgumentException("admin.login.error.badCredentials");
		}
		
		if (admin.getRole() != Role.ADMIN) {
			throw new IllegalArgumentException("admin.login.error.badCredentials");
		}
		
		if (!admin.isLoginable()) {
			throw new IllegalStateException("admin.login.error.unavailable");
		}
		
		return admin;
	}
	
	// 이메일은 숨기고 인증 키와 만료 시각만 내려보낸다.
	public record IssuedResult(String verificationKey, LocalDateTime expiresAt) {
	}
}
