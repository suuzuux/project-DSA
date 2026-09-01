package megane6.weplanet.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import megane6.weplanet.domain.entity.enumfolder.EmailVerificationPurpose;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(
		name = "email_verification",
		uniqueConstraints = {
				@UniqueConstraint(
						name = "uk_email_verification_key",
						columnNames = "verification_key"
				)
		},
		indexes = {
				@Index(
						name = "idx_email_verification_email",
						columnList = "email, purpose, created_at"
				),
				@Index(
						name = "idx_email_verification_user",
						columnList = "user_id, purpose, created_at"
				)
		}
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailVerification {
	
	public static final int MAX_ATTEMPTS = 5;
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	
	// 회원가입 인증은 가입 전이므로 null,
	// 프로젝트 등록 인증은 로그인 회원이 들어간다.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id")
	private User user;
	
	@Column(nullable = false, length = 255)
	private String email;
	
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private EmailVerificationPurpose purpose;
	
	@Column(name = "verification_key", nullable = false, unique = true, length = 36)
	private String verificationKey;
	
	@Column(name = "code_hash", nullable = false, length = 255)
	private String codeHash;
	
	@Column(name = "attempt_count", nullable = false)
	private int attemptCount;
	
	@Column(name = "expires_at", nullable = false)
	private LocalDateTime expiresAt;
	
	@Column(name = "verified_at")
	private LocalDateTime verifiedAt;
	
	@Column(name = "consumed_at")
	private LocalDateTime consumedAt;
	
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
	
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;
	
	private EmailVerification(
			User user,
			String email,
			EmailVerificationPurpose purpose,
			String codeHash,
			LocalDateTime expiresAt
	) {
		validate(email, purpose, codeHash, expiresAt);
		
		this.user = user;
		this.email = normalizeEmail(email);
		this.purpose = purpose;
		this.verificationKey = UUID.randomUUID().toString();
		this.codeHash = codeHash;
		this.attemptCount = 0;
		this.expiresAt = expiresAt;
	}
	
	public static EmailVerification createForSignup(
			String email,
			String codeHash,
			LocalDateTime expiresAt
	) {
		return new EmailVerification(
				null,
				email,
				EmailVerificationPurpose.SIGNUP,
				codeHash,
				expiresAt
		);
	}
	
	public static EmailVerification createForProject(
			User user,
			String codeHash,
			LocalDateTime expiresAt
	) {
		if (user == null) {
			throw new IllegalArgumentException("이메일 인증 회원이 필요합니다.");
		}
		
		return new EmailVerification(
				user,
				user.getEmail(),
				EmailVerificationPurpose.FAN_PROJECT_CREATE,
				codeHash,
				expiresAt
		);
	}
	
	// 최고관리자 로그인 2차 인증
	public static EmailVerification createForAdminLogin(
			User admin,
			String codeHash,
			LocalDateTime expiresAt
	) {
		if (admin == null) {
			throw new IllegalArgumentException("이메일 인증 회원이 필요합니다.");
		}
		
		return new EmailVerification(
				admin,
				admin.getEmail(),
				EmailVerificationPurpose.ADMIN_LOGIN,
				codeHash,
				expiresAt
		);
	}
	
	public boolean isExpired(LocalDateTime now) {
		return !now.isBefore(expiresAt);
	}
	
	public boolean isVerified() {
		return verifiedAt != null;
	}
	
	public boolean isConsumed() {
		return consumedAt != null;
	}
	
	public boolean hasAttemptsRemaining() {
		return attemptCount < MAX_ATTEMPTS;
	}
	
	public void recordFailedAttempt() {
		if (!hasAttemptsRemaining()) {
			throw new IllegalStateException("인증번호 입력 가능 횟수를 초과했습니다.");
		}
		
		attemptCount++;
	}
	
	public void markVerified(LocalDateTime now) {
		if (isConsumed()) {
			throw new IllegalStateException("이미 사용된 이메일 인증입니다.");
		}
		
		if (isVerified()) {
			throw new IllegalStateException("이미 완료된 이메일 인증입니다.");
		}
		
		if (isExpired(now)) {
			throw new IllegalStateException("이메일 인증번호가 만료되었습니다.");
		}
		
		if (!hasAttemptsRemaining()) {
			throw new IllegalStateException("인증번호 입력 가능 횟수를 초과했습니다.");
		}
		
		this.verifiedAt = now;
	}
	
	public void consume(LocalDateTime now) {
		if (!isVerified()) {
			throw new IllegalStateException("이메일 인증을 완료해주세요.");
		}
		
		if (isConsumed()) {
			throw new IllegalStateException("이미 사용된 이메일 인증입니다.");
		}
		
		if (isExpired(now)) {
			throw new IllegalStateException("이메일 인증이 만료되었습니다.");
		}
		
		this.consumedAt = now;
	}
	
	private static void validate(
			String email,
			EmailVerificationPurpose purpose,
			String codeHash,
			LocalDateTime expiresAt
	) {
		if (email == null || email.isBlank()) {
			throw new IllegalArgumentException("인증할 이메일 주소가 필요합니다.");
		}
		
		if (purpose == null) {
			throw new IllegalArgumentException("이메일 인증 목적이 필요합니다.");
		}
		
		if (codeHash == null || codeHash.isBlank()) {
			throw new IllegalArgumentException("이메일 인증번호 해시가 필요합니다.");
		}
		
		if (expiresAt == null || !expiresAt.isAfter(LocalDateTime.now())) {
			throw new IllegalArgumentException("이메일 인증 만료 시각이 올바르지 않습니다.");
		}
	}
	
	private static String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}
	
	@PrePersist
	private void prePersist() {
		LocalDateTime now = LocalDateTime.now();
		this.createdAt = now;
		this.updatedAt = now;
	}
	
	@PreUpdate
	private void preUpdate() {
		this.updatedAt = LocalDateTime.now();
	}
}