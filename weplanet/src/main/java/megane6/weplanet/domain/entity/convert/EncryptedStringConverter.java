package megane6.weplanet.domain.entity.convert;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import megane6.weplanet.security.PersonalDataCipher;
import org.springframework.stereotype.Component;

/**
 * 회원 실명·전화번호를 AES-GCM 으로 암호화해 VARBINARY 칸에 저장하고, 읽을 때 복호화한다.
 * 키가 필요해서 스프링 빈으로 만든다 (Hibernate 가 스프링에서 꺼내 쓴다).
 * 암호화 전에 평문으로 저장된 값은 그대로 읽는다 (PersonalDataCipher 참고).
 */
@Component
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, byte[]> {

	private final PersonalDataCipher cipher;

	public EncryptedStringConverter(PersonalDataCipher cipher) {
		this.cipher = cipher;
	}

	@Override
	public byte[] convertToDatabaseColumn(String attribute) {
		return cipher.encrypt(attribute);
	}

	@Override
	public String convertToEntityAttribute(byte[] dbData) {
		return cipher.decrypt(dbData);
	}
}
