package megane6.weplanet.service.agency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.EmailVerification;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.EmailVerificationPurpose;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.domain.entity.enumfolder.UserStatus;
import megane6.weplanet.repository.account.EmailVerificationRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.regex.Pattern;

/** 초대 링크 계정(소속사·아티스트) 활성화 - 추측 불가능한 긴 랜덤 토큰 사용. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgencyActivationService {
	// 메일 확인 기간 (72시간)
	public static final long EXPIRATION_HOURS = 72;
	
	private static final Pattern PASSWORD_PATTERN
			= Pattern.compile("^(?=.*[a-zA-Z])(?=.*[0-9]).{8,20}$");
	private static final int TOKEN_BYTE_LENGTH = 32;
	
	private final EmailVerificationRepository evr;
	private final PasswordEncoder pe;
	private final SecureRandom secureRandom = new SecureRandom();
	
	// 활성화 토큰 발급 (승인·재발송 공통)
	@Transactional
	public IssuedActivation issueActivationToken(User agencyUser) {
		if (agencyUser == null) {
			throw new IllegalArgumentException("활성화할 계정이 필요합니다.");
		}
		
		if (agencyUser.getStatus() != UserStatus.PENDING_ACTIVATION) {
			throw new IllegalStateException("error.activation.alreadyActivated");
		}
		
		String rawToken = generateToken();
		
		// DB 에는 해시만 저장하고, 링크 용도는 계정 역할로 정한다.
		EmailVerification saved = evr.save(
				createVerification(
						agencyUser,
						pe.encode(rawToken),
						LocalDateTime.now().plusHours(EXPIRATION_HOURS)
				)
		);
		log.info("소속사 계정 활성화 토큰 발급 : userId={}, expiresAt={}",
				agencyUser.getId(), saved.getExpiresAt());
		return new IssuedActivation(
				saved.getVerificationKey(),
				rawToken,
				saved.getExpiresAt()
		);
	}
	
	// 재발송 - 이전 링크를 모두 무효화하고 새 토큰을 발급한다.
	@Transactional
	public IssuedActivation reissueActivationToken(User agencyUser) {
		LocalDateTime now = LocalDateTime.now();
		
		evr.findByUser_IdAndPurposeAndConsumedAtIsNull(
				agencyUser.getId(),
				purposeFor(agencyUser))
				.forEach(previous -> previous.invalidate(now));
		
		return issueActivationToken(agencyUser);
	}
	
	// 활성화 화면을 열 때 링크 유효성 확인
	public ActivationTarget loadActivationTarget(
			String verificationKey,
			String rawToken
	) {
		EmailVerification verification = findByKey(verificationKey);
		
		assertUsable(verification, rawToken, LocalDateTime.now());
		
		User user = verification.getUser();
		
		return new ActivationTarget(
				user.getUsername(),
				user.getNickname(),
				user.getRole()
		);
	}
	
	// 비밀번호를 설정하고 계정을 활성화한다.
	@Transactional
	public User activate(
			String verificationKey,
			String rawToken,
			String newPassword,
			String confirmPassword
	) {
		LocalDateTime now = LocalDateTime.now();
		
		// 같은 링크 동시 요청을 막는 잠금 조회
		EmailVerification verification
				= evr.findByVerificationKeyForUpdate(verificationKey)
				.orElseThrow(() -> new IllegalArgumentException("error.activation.invalidLink"));
		
		assertUsable(verification, rawToken, now);
		assertPassword(newPassword, confirmPassword);
		
		User user = verification.getUser();
		user.activateWithPassword(pe.encode(newPassword));
		
		verification.markVerified(now);
		verification.consume(now);
		
		log.info("계정 활성화 완료: userId={}, role={}", user.getId(), user.getRole());
		
		return user;
	}
	
	private EmailVerification findByKey(String verificationKey) {
		if (verificationKey == null || verificationKey.isBlank()) {
			throw new IllegalArgumentException("error.activation.invalidLink");
		}
		
		return evr.findByVerificationKey(verificationKey)
				.orElseThrow(() -> new IllegalArgumentException("error.activation.invalidLink"));
	}
	
	private void assertUsable(
			EmailVerification verification,
			String rawToken,
			LocalDateTime now
	) {
		// 계정 역할과 링크 용도가 맞아야 한다.
		if (verification.getPurpose() != purposeFor(verification.getUser())) {
			throw new IllegalArgumentException("error.activation.invalidLink");
		}
		
		if (verification.isConsumed()) {
			throw new IllegalStateException("error.activation.linkUsed");
		}
		
		if (verification.isExpired(now)) {
			throw new IllegalStateException("error.activation.linkExpired");
		}
		
		if (!verification.hasAttemptsRemaining()) {
			throw new IllegalStateException("error.activation.tooManyAttempts");
		}
		
		if (rawToken == null || !pe.matches(rawToken, verification.getCodeHash())) {
			throw new IllegalArgumentException("error.activation.invalidLink");
		}
	}
	
	private void assertPassword(String newPassword, String confirmPassword) {
		if (newPassword == null || !PASSWORD_PATTERN.matcher(newPassword).matches()) {
			throw new IllegalArgumentException("signup.validation.passwordPattern");
		}
		
		if (!newPassword.equals(confirmPassword)) {
			throw new IllegalArgumentException("error.password.confirmMismatch");
		}
	}
	
	// 계정 역할 → 링크 용도 (소속사·아티스트만)
	private EmailVerificationPurpose purposeFor(User user) {
		if (user.getRole() == Role.AGENCY) {
			return EmailVerificationPurpose.AGENCY_ACTIVATION;
		}
		
		if (user.getRole() == Role.ARTIST) {
			return EmailVerificationPurpose.ARTIST_ACTIVATION;
		}
		
		throw new IllegalArgumentException("error.activation.invalidLink");
	}
	
	private EmailVerification createVerification(User user, String tokenHash, LocalDateTime expiresAt) {
		if (purposeFor(user) == EmailVerificationPurpose.ARTIST_ACTIVATION) {
			return EmailVerification.createForArtistActivation(user, tokenHash, expiresAt);
		}
		
		return EmailVerification.createForAgencyActivation(user, tokenHash, expiresAt);
	}
	
	private String generateToken() {
		byte[] buffer = new byte[TOKEN_BYTE_LENGTH];
		secureRandom.nextBytes(buffer);
		
		// URL 에 담으려고 URL-safe Base64 를 쓴다.
		return Base64.getUrlEncoder()
				.withoutPadding()
				.encodeToString(buffer);
	}
	
	// 발급 결과 (rawToken 은 메일 발송 때만 평문으로 존재)
	public record IssuedActivation(
			String verificationKey,
			String rawToken,
			LocalDateTime expiresAt
	){
	}
	
	// 활성화 화면 정보 (role 로 문구와 로그인 탭 구분)
	public record ActivationTarget(
			String username, String nickname, Role role
	) {}
}
