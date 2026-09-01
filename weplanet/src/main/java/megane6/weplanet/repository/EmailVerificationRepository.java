package megane6.weplanet.repository;

import jakarta.persistence.LockModeType;
import megane6.weplanet.domain.entity.EmailVerification;
import megane6.weplanet.domain.entity.enumfolder.EmailVerificationPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {
	// 인증키를 이용한 일반 조회
	Optional<EmailVerification> findByVerificationKey(String verificationKey);
	
	// 인증 성공 및 사용 완료 처리 시 중복 요청글을 막기 위한 잠금 조회
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			SELECT verification
			FROM EmailVerification verification
			LEFT JOIN FETCH verification.user
			WHERE verification.verificationKey = :verificationKey
			""")
	Optional<EmailVerification> findByVerificationKeyForUpdate(
			@Param("verificationKey") String verificationKey);
	
	// 회원가입 인증번호 재전송 제한 확인용
	Optional<EmailVerification> findTopByEmailAndPurposeOrderByCreatedAtDesc(
			String email,
			EmailVerificationPurpose purpose
	);
	
	// 프로젝트 등록 인증번호 재전송 제한 확인용
	Optional<EmailVerification> findTopByUser_IdAndPurposeOrderByCreatedAtDesc(
			Long userId,
			EmailVerificationPurpose emailVerificationPurpose
	);
}
