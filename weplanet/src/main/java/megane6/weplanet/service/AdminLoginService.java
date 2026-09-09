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
	// 메일 본문에 안내할 유효시간. EmailVerificationService 의 실제 만료 설정과 같은 값을 쓴다.
	private static final long EXPIRE_MINUTES = EmailVerificationService.ADMIN_EXPIRATION_MINUTES;
	
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
		
		// 이메일 주소는 화면에 노출하지 않는다. 타이머에 쓸 만료 시각만 내려보낸다.
		return new IssuedResult(issued.verificationKey(), issued.expiresAt());
	}

	/**
	 * [인증하기] 버튼 - 최종 로그인 전에 인증번호만 미리 확인한다.
	 * <p>
	 * 여기서 통과하면 인증 기록이 verified 상태가 되고,
	 * 최종 로그인(confirmCode)에서 다시 확인할 때 그대로 통과된다.
	 * <p>
	 * noRollbackFor : 인증번호가 틀렸을 때 던지는 예외로 "실패 횟수 증가"가
	 * 롤백되면 시도 제한이 무의미해지므로 롤백하지 않도록 지정한다.
	 */
	@Transactional(noRollbackFor = {IllegalArgumentException.class, IllegalStateException.class})
	public void verifyOnly(String username, String rawPassword, String verificationKey, String code) {
		User admin = authenticate(username, rawPassword);

		EmailVerificationService.VerificationResult result =
				evs.confirmAdminLoginVerification(admin.getId(), verificationKey, code);

		if (!result.verified()) {
			throw new IllegalArgumentException(
					"인증번호가 올바르지 않습니다. (남은 시도 " + result.remainingAttempts() + "회)"
			);
		}
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
	// 관리자 이메일은 화면에 노출하지 않는다(계정 정보 유출 방지).
	// 만료 시각만 내려보내 화면 타이머가 서버 기준으로 돌게 한다.
	public record IssuedResult(String verificationKey, LocalDateTime expiresAt) {
	}
}
