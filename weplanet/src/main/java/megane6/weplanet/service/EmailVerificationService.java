package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.EmailVerification;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.EmailVerificationPurpose;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.repository.EmailVerificationRepository;
import megane6.weplanet.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmailVerificationService {
	
	public static final long EXPIRATION_MINUTES = 5;
	private static final long RESEND_COOLDOWN_SECONDS = 60;

	// 관리자 로그인은 유효시간을 짧게 두고 재전송 제한은 두지 않는다.
	public static final long ADMIN_EXPIRATION_MINUTES = 2;
	
	private final EmailVerificationRepository evr;
	private final UserRepository ur;
	private final PasswordEncoder pe;
	
	private final SecureRandom secureRandom = new SecureRandom();
	
	// 회원가입 인증번호 발급
	@Transactional
	public IssuedVerification issueSignupVerification(String email) {
		String normalizedEmail = normalizeEmail(email);
		
		if (ur.existsByEmail(normalizedEmail)) {
			throw new IllegalArgumentException("error.email.alreadyRegistered");
		}
		
		LocalDateTime now = LocalDateTime.now();
		
		evr.findTopByEmailAndPurposeOrderByCreatedAtDesc(
						normalizedEmail,
						EmailVerificationPurpose.SIGNUP
				).ifPresent(latest -> assertResendAllowed(latest, now));
		
		String rawCode = generateCode();
		String codeHash = pe.encode(rawCode);
		
		EmailVerification verification =
				EmailVerification.createForSignup(
						normalizedEmail,
						codeHash,
						now.plusMinutes(EXPIRATION_MINUTES)
				);
		
		EmailVerification saved =
				evr.save(verification);
		
		return new IssuedVerification(
				saved.getVerificationKey(),
				saved.getEmail(),
				rawCode,
				saved.getExpiresAt()
		);
	}
	
	// 프로젝트 등록 인증번호 발급
	@Transactional
	public IssuedVerification issueProjectVerification(Long userId) {
		User user = ur.findById(userId)
				.orElseThrow(() ->
						new IllegalArgumentException("error.project.memberNotFound")
				);
		
		if (user.getRole() != Role.FAN) {
			throw new IllegalStateException(
					"error.email.projectFanOnly"
			);
		}
		
		if (user.getEmailVerifiedAt() == null) {
			throw new IllegalStateException(
					"error.email.signupNotVerified"
			);
		}
		
		LocalDateTime now = LocalDateTime.now();
		
		evr.findTopByUser_IdAndPurposeOrderByCreatedAtDesc(
						userId,
						EmailVerificationPurpose.FAN_PROJECT_CREATE
				).ifPresent(latest -> assertResendAllowed(latest, now));
		
		String rawCode = generateCode();
		String codeHash = pe.encode(rawCode);
		
		EmailVerification verification =
				EmailVerification.createForProject(
						user,
						codeHash,
						now.plusMinutes(EXPIRATION_MINUTES)
				);
		
		EmailVerification saved =
				evr.save(verification);
		
		return new IssuedVerification(
				saved.getVerificationKey(),
				saved.getEmail(),
				rawCode,
				saved.getExpiresAt()
		);
	}
	
	// 관리자 로그인 인증번호 발급 (아이디·비밀번호 확인 후, 관리자 이메일로만 발송)
	@Transactional
	public IssuedVerification issueAdminLoginVerification(Long adminId) {
		User admin = ur.findById(adminId).orElseThrow(() ->
				new IllegalArgumentException("error.email.adminNotFound"));
		
		if (admin.getRole() != Role.ADMIN) {
			throw new IllegalStateException("error.email.superAdminOnly");
		}
		
		LocalDateTime now = LocalDateTime.now();

		// 재전송 제한 없이 새 번호 발급 시 이전 번호를 폐기한다.
		evr.findByUser_IdAndPurposeAndConsumedAtIsNull(
				adminId,
				EmailVerificationPurpose.ADMIN_LOGIN
		).forEach(previous -> previous.invalidate(now));

		String rawCode = generateCode();
		String codeHash = pe.encode(rawCode);

		EmailVerification saved = evr.save(
				EmailVerification.createForAdminLogin(
						admin,
						codeHash,
						now.plusMinutes(ADMIN_EXPIRATION_MINUTES)
				)
		);

		return new IssuedVerification(
				saved.getVerificationKey(),
				saved.getEmail(),
				rawCode,
				saved.getExpiresAt()
		);
	}
	
	// 회원가입 인증번호 확인
	@Transactional
	public VerificationResult confirmSignupVerification(
			String verificationKey,
			String email,
			String rawCode
	) {
		EmailVerification verification =
				findForUpdate(verificationKey);
		
		assertTarget(
				verification,
				EmailVerificationPurpose.SIGNUP,
				null,
				normalizeEmail(email)
		);
		
		return verifyCode(verification, rawCode);
	}
	
	// 프로젝트 등록 인증번호 확인
	@Transactional
	public VerificationResult confirmProjectVerification(
			Long userId,
			String verificationKey,
			String rawCode
	) {
		EmailVerification verification =
				findForUpdate(verificationKey);
		
		assertTarget(
				verification,
				EmailVerificationPurpose.FAN_PROJECT_CREATE,
				userId,
				null
		);
		
		return verifyCode(verification, rawCode);
	}
	
	// 관리자 로그인 인증번호 확인
	@Transactional
	public VerificationResult confirmAdminLoginVerification(
			Long adminId,
			String verificationKey,
			String rawCode
	) {
		EmailVerification verification = findForUpdate(verificationKey);
		assertTarget(
				verification,
				EmailVerificationPurpose.ADMIN_LOGIN,
				adminId,
				null
		);
		return verifyCode(verification, rawCode);
	}
	
	// 회원가입 저장 시 인증 기록 사용 완료
	@Transactional
	public LocalDateTime consumeSignupVerification(
			String verificationKey,
			String email
	) {
		EmailVerification verification =
				findForUpdate(verificationKey);
		
		assertTarget(
				verification,
				EmailVerificationPurpose.SIGNUP,
				null,
				normalizeEmail(email)
		);
		
		LocalDateTime now = LocalDateTime.now();
		verification.consume(now);
		
		return verification.getVerifiedAt();
	}
	
	// 프로젝트 저장 시 인증 기록 사용 완료
	@Transactional
	public LocalDateTime consumeProjectVerification(
			Long userId,
			String verificationKey
	) {
		EmailVerification verification =
				findForUpdate(verificationKey);
		
		assertTarget(
				verification,
				EmailVerificationPurpose.FAN_PROJECT_CREATE,
				userId,
				null
		);
		
		LocalDateTime now = LocalDateTime.now();
		verification.consume(now);
		
		return verification.getVerifiedAt();
	}

	// 관리자 로그인 완료 시 인증번호 사용 완료
	@Transactional
	public LocalDateTime consumeAdminLoginVerification(
			Long adminId,
			String verificationKey
	) {
		EmailVerification verification =
				findForUpdate(verificationKey);

		assertTarget(
				verification,
				EmailVerificationPurpose.ADMIN_LOGIN,
				adminId,
				null
		);

		LocalDateTime now = LocalDateTime.now();
		verification.consume(now);

		return verification.getVerifiedAt();
	}
	
	private EmailVerification findForUpdate(String verificationKey) {
		if (verificationKey == null || verificationKey.isBlank()) {
			throw new IllegalArgumentException("error.email.verificationRequired");
		}
		
		return evr.findByVerificationKeyForUpdate(verificationKey).orElseThrow(() ->
				new IllegalArgumentException("error.email.verificationNotFound"));
	}
	
	private VerificationResult verifyCode(
			EmailVerification verification,
			String rawCode
	) {
		LocalDateTime now = LocalDateTime.now();
		
		if (verification.isConsumed()) {
			throw new IllegalStateException(
					"error.email.alreadyUsed"
			);
		}
		
		if (verification.isExpired(now)) {
			throw new IllegalStateException(
					"error.email.codeExpired"
			);
		}
		
		// 이미 인증된 요청이면 시각을 바꾸지 않고 성공 처리
		if (verification.isVerified()) {
			return new VerificationResult(
					true,
					EmailVerification.MAX_ATTEMPTS
							- verification.getAttemptCount()
			);
		}
		
		if (!verification.hasAttemptsRemaining()) {
			throw new IllegalStateException(
					"error.email.tooManyAttempts"
			);
		}
		
		boolean matches =
				rawCode != null
						&& pe.matches(
						rawCode.trim(),
						verification.getCodeHash()
				);
		
		if (!matches) {
			verification.recordFailedAttempt();
			
			return new VerificationResult(
					false,
					EmailVerification.MAX_ATTEMPTS
							- verification.getAttemptCount()
			);
		}
		
		verification.markVerified(now);
		
		return new VerificationResult(
				true,
				EmailVerification.MAX_ATTEMPTS
						- verification.getAttemptCount()
		);
	}
	
	private void assertTarget(
			EmailVerification verification,
			EmailVerificationPurpose expectedPurpose,
			Long expectedUserId,
			String expectedEmail
	) {
		if (verification.getPurpose() != expectedPurpose) {
			throw new IllegalArgumentException(
					"error.email.purposeMismatch"
			);
		}
		
		if (expectedUserId == null) {
			if (verification.getUser() != null) {
				throw new IllegalArgumentException(
						"error.email.notSignupVerification"
				);
			}
			
			if (!verification.getEmail().equals(expectedEmail)) {
				throw new IllegalArgumentException(
						"error.email.addressMismatch"
				);
			}
			
			return;
		}
		
		if (verification.getUser() == null
				|| !verification.getUser().getId().equals(expectedUserId)) {
			throw new IllegalArgumentException(
					"error.email.notOwnVerification"
			);
		}
		
		String currentEmail =
				normalizeEmail(verification.getUser().getEmail());
		
		if (!verification.getEmail().equals(currentEmail)) {
			throw new IllegalStateException(
					"error.email.addressChanged"
			);
		}
	}
	
	private void assertResendAllowed(
			EmailVerification latest,
			LocalDateTime now
	) {
		LocalDateTime resendAvailableAt =
				latest.getCreatedAt()
						.plusSeconds(RESEND_COOLDOWN_SECONDS);
		
		if (resendAvailableAt.isAfter(now)) {
			throw new IllegalStateException(
					"error.email.resendCooldown"
			);
		}
	}
	
	private String generateCode() {
		int number = secureRandom.nextInt(1_000_000);
		return String.format("%06d", number);
	}
	
	private static String normalizeEmail(String email) {
		if (email == null || email.isBlank()) {
			throw new IllegalArgumentException(
					"error.email.addressRequired"
			);
		}
		
		return email.trim().toLowerCase(Locale.ROOT);
	}
	
	// 발송 서비스에만 전달한다 (응답에 rawCode 포함 금지).
	public record IssuedVerification(
			String verificationKey,
			String recipientEmail,
			String rawCode,
			// 화면 타이머용 서버 만료 시각
			LocalDateTime expiresAt
	) {
	}
	
	public record VerificationResult(
			boolean verified,
			int remainingAttempts
	) {
	}
}
