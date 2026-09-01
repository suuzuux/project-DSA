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
	
	private static final long EXPIRATION_MINUTES = 5;
	private static final long RESEND_COOLDOWN_SECONDS = 60;
	
	private final EmailVerificationRepository evr;
	private final UserRepository ur;
	private final PasswordEncoder pe;
	
	private final SecureRandom secureRandom = new SecureRandom();
	
	// 회원가입 인증번호 발급
	@Transactional
	public IssuedVerification issueSignupVerification(String email) {
		String normalizedEmail = normalizeEmail(email);
		
		if (ur.existsByEmail(normalizedEmail)) {
			throw new IllegalArgumentException("이미 가입된 이메일입니다.");
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
				rawCode
		);
	}
	
	// 프로젝트 등록 인증번호 발급
	@Transactional
	public IssuedVerification issueProjectVerification(Long userId) {
		User user = ur.findById(userId)
				.orElseThrow(() ->
						new IllegalArgumentException("로그인 회원을 찾을 수 없습니다.")
				);
		
		if (user.getRole() != Role.FAN) {
			throw new IllegalStateException(
					"팬 회원만 프로젝트 등록 이메일 인증을 진행할 수 있습니다."
			);
		}
		
		if (user.getEmailVerifiedAt() == null) {
			throw new IllegalStateException(
					"회원가입 이메일 인증이 완료되지 않았습니다."
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
				rawCode
		);
	}
	
	// 최고관리자 로그인 인증번호 발급
	// ID/PW가 이미 검증된 뒤 호출. 대상 관리자 확정 및 그 계정에 등록된 이메일로만 발송됨
	@Transactional
	public IssuedVerification issueAdminLoginVerification(Long adminId) {
		User admin = ur.findById(adminId).orElseThrow(() ->
				new IllegalArgumentException("관리자 계정을 찾을 수 없습니다."));
		
		if (admin.getRole() != Role.ADMIN) {
			throw new IllegalStateException("최고관리자만 사용할 수 있습니다.");
		}
		
		LocalDateTime now = LocalDateTime.now();
		
		evr.findTopByUser_IdAndPurposeOrderByCreatedAtDesc(
				adminId,
				EmailVerificationPurpose.ADMIN_LOGIN
		).ifPresent(latest -> assertResendAllowed(latest, now));
		
		String rawCode = generateCode();
		String codeHash = pe.encode(rawCode);
		
		EmailVerification saved = evr.save(
				EmailVerification.createForAdminLogin(
						admin,
						codeHash,
						now.plusMinutes(EXPIRATION_MINUTES)
				)
		);
		
		return new IssuedVerification(
				saved.getVerificationKey(),
				saved.getEmail(),
				rawCode
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
	
	// 최고관리자 로그인 인증번호
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
	
	// 회원가입이 최종 저장될 때 인증 기록을 사용 완료 처리
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
	
	// 프로젝트가 최종 저장될 때 인증 기록을 사용 완료 처리
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
	
	private EmailVerification findForUpdate(String verificationKey) {
		if (verificationKey == null || verificationKey.isBlank()) {
			throw new IllegalArgumentException("이메일 인증 정보가 필요합니다.");
		}
		
		return evr.findByVerificationKeyForUpdate(verificationKey).orElseThrow(() ->
				new IllegalArgumentException("이메일 인증 정보를 찾을 수 없습니다."));
	}
	
	private VerificationResult verifyCode(
			EmailVerification verification,
			String rawCode
	) {
		LocalDateTime now = LocalDateTime.now();
		
		if (verification.isConsumed()) {
			throw new IllegalStateException(
					"이미 사용된 이메일 인증입니다."
			);
		}
		
		if (verification.isExpired(now)) {
			throw new IllegalStateException(
					"이메일 인증번호가 만료되었습니다."
			);
		}
		
		// 같은 확인 요청이 다시 들어오면 인증 시각을 바꾸지 않고 성공 처리
		if (verification.isVerified()) {
			return new VerificationResult(
					true,
					EmailVerification.MAX_ATTEMPTS
							- verification.getAttemptCount()
			);
		}
		
		if (!verification.hasAttemptsRemaining()) {
			throw new IllegalStateException(
					"인증번호 입력 가능 횟수를 초과했습니다."
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
					"이메일 인증 목적이 일치하지 않습니다."
			);
		}
		
		if (expectedUserId == null) {
			if (verification.getUser() != null) {
				throw new IllegalArgumentException(
						"회원가입 이메일 인증 정보가 아닙니다."
				);
			}
			
			if (!verification.getEmail().equals(expectedEmail)) {
				throw new IllegalArgumentException(
						"인증한 이메일 주소가 일치하지 않습니다."
				);
			}
			
			return;
		}
		
		if (verification.getUser() == null
				|| !verification.getUser().getId().equals(expectedUserId)) {
			throw new IllegalArgumentException(
					"현재 회원의 이메일 인증 정보가 아닙니다."
			);
		}
		
		String currentEmail =
				normalizeEmail(verification.getUser().getEmail());
		
		if (!verification.getEmail().equals(currentEmail)) {
			throw new IllegalStateException(
					"회원 이메일이 변경되었습니다. 다시 인증해주세요."
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
					"인증번호는 60초 후 다시 전송할 수 있습니다."
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
					"인증할 이메일 주소가 필요합니다."
			);
		}
		
		return email.trim().toLowerCase(Locale.ROOT);
	}
	
	// 이 값은 메일 발송 Service에만 전달한다.
	// Controller 응답에 rawCode를 포함하면 안 된다.
	public record IssuedVerification(
			String verificationKey,
			String recipientEmail,
			String rawCode
	) {
	}
	
	public record VerificationResult(
			boolean verified,
			int remainingAttempts
	) {
	}
}