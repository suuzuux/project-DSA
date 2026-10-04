package megane6.weplanet.domain.entity.convert;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.nio.charset.StandardCharsets;


/**
 * 지금은 암호화 없이 문자열 ↔ 바이트만 변환한다 (VARBINARY 컬럼 구조를 미리 맞춰 둔 것).
 * 나중에 AES 암호화를 붙일 때 이 클래스 안만 고치면 된다.
 */

@Converter
public class PlaintextBytesConverter implements AttributeConverter<String, byte[]> {
	
	@Override
	public byte[] convertToDatabaseColumn(String attribute) {
		return attribute == null ? null : attribute.getBytes(StandardCharsets.UTF_8);
	}
	
	@Override
	public String convertToEntityAttribute(byte[] dbData) {
		return dbData == null ? null : new String(dbData, StandardCharsets.UTF_8);
	}
}
