package megane6.weplanet.domain.entity.convert;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.nio.charset.StandardCharsets;


/** 문자열 ↔ 바이트 변환 (VARBINARY 컬럼용, 암호화를 붙일 자리). */

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
