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
	
	// 입점 승인 후 발급하는 소속사 계정 활성화 토큰.
	// 다른 용도와 달리 6자리 숫자가 아니라 긴 랜덤 문자열을 쓰고,
	// 메일 링크에 담아서 보내브로 codeHash에는 그 토큰의 해시만 저장한다.
	public static EmailVerification createForAgencyActivation(
			User agencyUser,
			String tokenHash,
			LocalDateTime expiresAt
	) {
		if (agencyUser == null) {
			throw new IllegalArgumentException("이메일 인증 회원이 필요합니다.");
		}
		
		return new EmailVerification(
				agencyUser,
				agencyUser.getEmail(),
				EmailVerificationPurpose.AGENCY_ACTIVATION,
				tokenHash,
				expiresAt
		);
	}
	
	// 소속사가 포털에서 등록한 아티스트 그룹 계정의 활성화 링크
	// 소속사 활성화와 구조는 같고, purpose만 다르다
	// purpose를 나눠야 소속사용 링크로 아티스트 계정을, 아티스트용 링크로 소속사 계정을 여는 일이 없다
	public static EmailVerification createForArtistActivation(
			User artistUser,
			String tokenHash,
			LocalDateTime expiresAt
	) {
		if (artistUser == null) {
			throw new IllegalArgumentException("이메일 인증 회원이 필요합니다.");
		}
		
		return new EmailVerification(
				artistUser,
				artistUser.getEmail(),
				EmailVerificationPurpose.ARTIST_ACTIVATION,
				tokenHash,
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
			throw new IllegalStateException("error.email.tooManyAttempts");
		}
		
		attemptCount++;
	}
	
	public void markVerified(LocalDateTime now) {
		if (isConsumed()) {
			throw new IllegalStateException("error.email.alreadyUsed");
		}
		
		if (isVerified()) {
			throw new IllegalStateException("error.email.alreadyVerified");
		}
		
		if (isExpired(now)) {
			throw new IllegalStateException("error.email.codeExpired");
		}
		
		if (!hasAttemptsRemaining()) {
			throw new IllegalStateException("error.email.tooManyAttempts");
		}
		
		this.verifiedAt = now;
	}
	
	public void consume(LocalDateTime now) {
		if (!isVerified()) {
			throw new IllegalStateException("error.email.verifyFirst");
		}
		
		if (isConsumed()) {
			throw new IllegalStateException("error.email.alreadyUsed");
		}
		
		if (isExpired(now)) {
			throw new IllegalStateException("error.email.verificationExpired");
		}
		
		this.consumedAt = now;
	}
	
	// 활성화 메일을 재발송할 때 이전에 보낸 링크가 더 이상 못 쓰게 만든다.
	// 만료 시각을 지금으로 당겨서 isExpired()가 true가 되게 한다.
	public void invalidate(LocalDateTime now) {
		if (isConsumed() || isExpired(now)) {
			return;
		}
		
		this.expiresAt = now;
	}
	
	private static void validate(
			String email,
			EmailVerificationPurpose purpose,
			String codeHash,
			LocalDateTime expiresAt
	) {
		if (email == null || email.isBlank()) {
			throw new IllegalArgumentException("error.email.addressRequired");
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