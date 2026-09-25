package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.EmailVerification;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.EmailVerificationPurpose;
import megane6.weplanet.domain.entity.enumfolder.UserStatus;
import megane6.weplanet.repository.EmailVerificationRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * 입점 승인으로 만들어진 소속사 계정의 활성화(비밀번호 설정)를 담당
 * 회원가입 인증과 달리 6자리 숫자가 아니라 긴 랜덤 토큰 사용
 * 메일 링크를 그냥 누르면 되도록 만들기 위해서이고, 대신 추측이 불가능하도록 길이를 충분히 길게 잡음
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgencyActivationService {
	// 관리자가 승인한 뒤 소속사가 메일을 확인할 시간을 넉넉히 줌
	public static final long EXPIRATION_HOURS = 72;
	
	private static final Pattern PASSWORD_PATTERN
			= Pattern.compile("^(?=.*[a-zA-Z])(?=.*[0-9]).{8,20}$");
	private static final int TOKEN_BYTE_LENGTH = 32;
	
	private final EmailVerificationRepository evr;
	private final PasswordEncoder pe;
	private final SecureRandom secureRandom = new SecureRandom();
	
	// 활성화 토근 발급. 승인 처리와 "초대 메일 재발송" 두 곳에서 쓴다.
	@Transactional
	public IssuedActivation issueActivationToken(User agencyUser) {
		if (agencyUser == null) {
			throw new IllegalArgumentException("소속사 계정이 필요합니다.");
		}
		
		if (agencyUser.getStatus() != UserStatus.PENDING_ACTIVATION) {
			throw new IllegalStateException("이미 활성화된 계정입니다.");
		}
		
		String rawToken = generateToken();
		
		// DB에는 해시만 저장. DB가 유출돼도 활성화 링크를 만들어낼 수 없음
		EmailVerification saved = evr.save(
				EmailVerification.createForAgencyActivation(
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
	
	// 활성화 화면을 열 때 링크가 아직 쓸 수 있는지 확인
	public ActivationTarget loadActivationTarget(
			String verificationKey,
			String rawToken
	) {
		EmailVerification verification = findByKey(verificationKey);
		
		assertUsable(verification, rawToken, LocalDateTime.now());
		
		User user = verification.getUser();
		
		return new ActivationTarget(
				user.getUsername(),
				user.getNickname()
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
		
		// 같은 링크로 동시에 두 번 들어오는 경우를 막기 위해 잠금 조회를 사용
		EmailVerification verification
				= evr.findByVerificationKeyForUpdate(verificationKey)
				.orElseThrow(() -> new IllegalArgumentException("유효하지 않은 활성화 링크입니다."));
		
		assertUsable(verification, rawToken, now);
		assertPassword(newPassword, confirmPassword);
		
		User user = verification.getUser();
		user.activateWithPassword(pe.encode(newPassword));
		
		verification.markVerified(now);
		verification.consume(now);
		
		log.info("소속사 계정 활성화 완료: userId={}", user.getId());
		
		return user;
	}
	
	private EmailVerification findByKey(String verificationKey) {
		if (verificationKey == null || verificationKey.isBlank()) {
			throw new IllegalArgumentException("유효하지 않은 활성화 링크입니다.");
		}
		
		return evr.findByVerificationKey(verificationKey)
				.orElseThrow(() -> new IllegalArgumentException("유효하지 않은 활성화 링크입니다."));
	}
	
	private void assertUsable(
			EmailVerification verification,
			String rawToken,
			LocalDateTime now
	) {
		if (verification.getPurpose() != EmailVerificationPurpose.AGENCY_ACTIVATION) {
			throw new IllegalArgumentException("유효하지 않은 활성화 링크입니다.");
		}
		
		if (verification.isConsumed()) {
			throw new IllegalStateException("이미 사용된 활성화 링크입니다. 로그인 화면에서 로그인해주세요.");
		}
		
		if (verification.isExpired(now)) {
			throw new IllegalStateException("활성화 링크가 만료되었습니다. 관리자에게 재발송을 요청해주세요.");
		}
		
		if (!verification.hasAttemptsRemaining()) {
			throw new IllegalStateException("활성화 시도 횟수를 초과했습니다. 관리자에게 재발송을 요청해주세요.");
		}
		
		if (rawToken == null || !pe.matches(rawToken, verification.getCodeHash())) {
			throw new IllegalArgumentException("유효하지 않은 활성화 링크입니다.");
		}
	}
	
	private void assertPassword(String newPassword, String confirmPassword) {
		if (newPassword == null || !PASSWORD_PATTERN.matcher(newPassword).matches()) {
			throw new IllegalArgumentException("비밀번호는 영문/숫자 포함 8~20자로 입력해주세요.");
		}
		
		if (!newPassword.equals(confirmPassword)) {
			throw new IllegalArgumentException("비밀번호 확인이 일치하지 않습니다.");
		}
	}
	
	private String generateToken() {
		byte[] buffer = new byte[TOKEN_BYTE_LENGTH];
		secureRandom.nextBytes(buffer);
		
		// URL에 그대로 담아야 하므로 URL-safe Base64를 쓴다
		return Base64.getUrlEncoder()
				.withoutPadding()
				.encodeToString(buffer);
	}
	
	// 발급 결과. rawToken은 메일에 담기 위해 이 순간에만 평문으로 존재한다.
	public record IssuedActivation(
			String verificationKey,
			String rawToken,
			LocalDateTime expiresAt
	){
	}
	
	// 활성화 화면에 보여줄 정보
	public record ActivationTarget(
			String username, String nickname
	) {}
}
