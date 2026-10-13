package megane6.weplanet.domain.entity.enumfolder;

// 지원 언어 - 기본 서비스 언어이자 AI 번역 대상 언어.
public enum Language {
	KO, JA, EN;

	// Gemini 번역 프롬프트에 쓰는 한국어 언어 이름
	public String displayNameKo() {
		return switch (this) {
			case KO -> "한국어";
			case JA -> "일본어";
			case EN -> "영어";
		};
	}
}
