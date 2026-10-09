package megane6.weplanet.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * 회원 개인정보(실명·전화번호) AES-GCM 암호화. 정산 계좌(AccountProtectionService)와 같은 방식이다.
 * 저장 형식: [표시 1바이트][IV 12바이트][암호문 + 인증 태그 16바이트] - 같은 값도 매번 다른 암호문이 된다.
 * 표시 바이트가 없는 값은 암호화 전에 저장된 평문(UTF-8)으로 보고 그대로 읽는다 (기존 데이터·데모 시드).
 * 개인정보 키를 따로 두기 전에 정산 계좌 키로 암호화한 값도 읽을 수 있게, 복호화는 정산 계좌 키로도 한 번 더 시도한다.
 */
@Component
public class PersonalDataCipher {

	static final byte FORMAT_V1 = 0x01;
	private static final int IV_LENGTH = 12;
	private static final int GCM_TAG_BITS = 128;
	static final int OVERHEAD = 1 + IV_LENGTH + GCM_TAG_BITS / 8;

	private final SecretKey encryptionKey;
	private final List<SecretKey> decryptionKeys = new ArrayList<>();
	private final SecureRandom secureRandom = new SecureRandom();

	public PersonalDataCipher(@Value("${personal-data.encryption-key}") String encryptionKey,
							  @Value("${fan-project.account.encryption-key}") String fallbackKey) {
		this.encryptionKey = toKey(encryptionKey);
		decryptionKeys.add(this.encryptionKey);
		SecretKey fallback = toKey(fallbackKey);
		if (!Arrays.equals(fallback.getEncoded(), this.encryptionKey.getEncoded())) {
			decryptionKeys.add(fallback);
		}
	}

	public byte[] encrypt(String plain) {
		if (plain == null) {
			return null;
		}
		try {
			byte[] iv = new byte[IV_LENGTH];
			secureRandom.nextBytes(iv);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
			byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
			return ByteBuffer.allocate(1 + iv.length + encrypted.length)
					.put(FORMAT_V1).put(iv).put(encrypted).array();
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("개인정보 암호화에 실패했습니다.", e);
		}
	}

	public String decrypt(byte[] stored) {
		if (stored == null) {
			return null;
		}
		if (!isEncrypted(stored)) {
			return new String(stored, StandardCharsets.UTF_8);
		}
		for (SecretKey key : decryptionKeys) {
			try {
				Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
				cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, stored, 1, IV_LENGTH));
				byte[] plain = cipher.doFinal(stored, 1 + IV_LENGTH, stored.length - 1 - IV_LENGTH);
				return new String(plain, StandardCharsets.UTF_8);
			} catch (GeneralSecurityException ignored) {
				// 다음 키로 시도
			}
		}
		// 엉뚱한 값을 보여주거나 덮어쓰지 않도록 멈춘다 (키가 바뀌었거나 데이터가 손상됨)
		throw new IllegalStateException("개인정보를 복호화하지 못했습니다. 암호화 키(PERSONAL_DATA_ENCRYPTION_KEY)를 확인하세요.");
	}

	// 암호화된 값인지 (표시 바이트 + 최소 길이). 평문 실명·전화번호는 UTF-8 글자라 0x01 로 시작하지 않는다
	public boolean isEncrypted(byte[] stored) {
		return stored != null && stored.length >= OVERHEAD && stored[0] == FORMAT_V1;
	}

	private static SecretKey toKey(String base64) {
		byte[] bytes = Base64.getDecoder().decode(base64.trim());
		if (bytes.length != 32) {
			throw new IllegalArgumentException("개인정보 암호화 키는 32바이트(Base64)여야 합니다.");
		}
		return new SecretKeySpec(bytes, "AES");
	}
}
