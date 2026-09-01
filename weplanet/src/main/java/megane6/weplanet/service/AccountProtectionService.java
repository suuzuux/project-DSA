package megane6.weplanet.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class AccountProtectionService {
	
	private static final int IV_LENGTH = 12;
	private static final int GCM_TAG_LENGTH = 128;
	
	private final SecretKey encryptionKey;
	private final SecretKey hmacKey;
	private final SecureRandom secureRandom = new SecureRandom();
	
	public AccountProtectionService(
			@Value("${fan-project.account.encryption-key}") String encryptionKey,
			@Value("${fan-project.account.hmac-key}") String hmacKey
	) {
		byte[] encryptionKeyBytes =
				Base64.getDecoder().decode(encryptionKey);
		byte[] hmacKeyBytes =
				Base64.getDecoder().decode(hmacKey);
		
		if (encryptionKeyBytes.length != 32) {
			throw new IllegalArgumentException(
					"계좌번호 암호화 키는 32바이트여야 합니다."
			);
		}
		
		if (hmacKeyBytes.length < 32) {
			throw new IllegalArgumentException(
					"계좌번호 HMAC 키는 32바이트 이상이어야 합니다."
			);
		}
		
		this.encryptionKey =
				new SecretKeySpec(encryptionKeyBytes, "AES");
		this.hmacKey =
				new SecretKeySpec(hmacKeyBytes, "HmacSHA256");
	}
	
	public ProtectedAccountNumber protect(String accountNumber) {
		if (accountNumber == null
				|| !accountNumber.matches("^[0-9]{6,30}$")) {
			throw new IllegalArgumentException(
					"계좌번호는 하이픈 없이 숫자만 입력해주세요."
			);
		}
		
		return new ProtectedAccountNumber(
				encrypt(accountNumber),
				createHmac(accountNumber),
				accountNumber.substring(accountNumber.length() - 4)
		);
	}
	
	private byte[] encrypt(String accountNumber) {
		try {
			byte[] iv = new byte[IV_LENGTH];
			secureRandom.nextBytes(iv);
			
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(
					Cipher.ENCRYPT_MODE,
					encryptionKey,
					new GCMParameterSpec(GCM_TAG_LENGTH, iv)
			);
			
			byte[] encrypted = cipher.doFinal(
					accountNumber.getBytes(StandardCharsets.UTF_8)
			);
			
			return ByteBuffer
					.allocate(iv.length + encrypted.length)
					.put(iv)
					.put(encrypted)
					.array();
			
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException(
					"계좌번호 암호화에 실패했습니다.", e
			);
		}
	}
	
	private String createHmac(String accountNumber) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(hmacKey);
			
			byte[] result = mac.doFinal(
					accountNumber.getBytes(StandardCharsets.UTF_8)
			);
			
			return HexFormat.of().formatHex(result);
			
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException(
					"계좌번호 HMAC 생성에 실패했습니다.", e
			);
		}
	}
	
	public record ProtectedAccountNumber(
			byte[] encrypted,
			String hmac,
			String last4
	) {
	}
}