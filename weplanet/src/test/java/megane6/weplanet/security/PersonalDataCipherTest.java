package megane6.weplanet.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersonalDataCipherTest {

	private static final String KEY_A = key(1);
	private static final String KEY_B = key(2);
	private static final String KEY_C = key(3);

	private final PersonalDataCipher cipher = new PersonalDataCipher(KEY_A, KEY_A);

	// 암호화한 실명·전화번호를 다시 풀면 원래 값이 나온다
	@Test
	void encryptsAndDecryptsNameAndPhone() {
		assertEquals("홍길동", cipher.decrypt(cipher.encrypt("홍길동")));
		assertEquals("010-1111-2222", cipher.decrypt(cipher.encrypt("010-1111-2222")));
	}

	// DB 에는 원래 글자가 보이지 않고, 같은 값도 매번 다른 암호문이 된다 (형식: 표시 1 + IV 12 + 암호문 + 태그 16)
	@Test
	void storedBytesAreNotPlainTextAndDifferEachTime() {
		byte[] first = cipher.encrypt("010-1111-2222");
		byte[] second = cipher.encrypt("010-1111-2222");

		assertFalse(Arrays.equals(first, second));
		assertEquals(PersonalDataCipher.FORMAT_V1, first[0]);
		assertEquals("010-1111-2222".length() + PersonalDataCipher.OVERHEAD, first.length);
		assertFalse(new String(first, StandardCharsets.UTF_8).contains("1111"));
		assertTrue(cipher.isEncrypted(first));
	}

	// 암호화 전에 평문으로 저장된 값(기존 데이터·데모 시드)은 그대로 읽는다
	@Test
	void readsLegacyPlainText() {
		byte[] legacy = "QA데모팬".getBytes(StandardCharsets.UTF_8);

		assertFalse(cipher.isEncrypted(legacy));
		assertEquals("QA데모팬", cipher.decrypt(legacy));
	}

	@Test
	void nullStaysNull() {
		assertNull(cipher.encrypt(null));
		assertNull(cipher.decrypt(null));
	}

	// 다른 키로는 풀리지 않는다 - 엉뚱한 값을 보여주지 않고 오류를 낸다
	@Test
	void wrongKeyFails() {
		byte[] stored = cipher.encrypt("홍길동");
		PersonalDataCipher other = new PersonalDataCipher(KEY_B, KEY_C);

		assertThrows(IllegalStateException.class, () -> other.decrypt(stored));
	}

	// 개인정보 키를 따로 두기 전에 정산 계좌 키로 암호화한 값도, 새 키로 바꾼 뒤 읽을 수 있다
	@Test
	void readsValueEncryptedWithFallbackKeyAfterNewKeyIsSet() {
		byte[] encryptedWithAccountKey = new PersonalDataCipher(KEY_B, KEY_B).encrypt("010-3333-4444");
		PersonalDataCipher withNewKey = new PersonalDataCipher(KEY_A, KEY_B);

		assertEquals("010-3333-4444", withNewKey.decrypt(encryptedWithAccountKey));
		assertArrayEquals(new byte[]{PersonalDataCipher.FORMAT_V1}, new byte[]{withNewKey.encrypt("x")[0]});
	}

	@Test
	void keyMustBe32Bytes() {
		String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

		assertThrows(IllegalArgumentException.class, () -> new PersonalDataCipher(shortKey, KEY_A));
	}

	private static String key(int fill) {
		byte[] bytes = new byte[32];
		Arrays.fill(bytes, (byte) fill);
		return Base64.getEncoder().encodeToString(bytes);
	}
}
