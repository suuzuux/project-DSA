package megane6.weplanet.domain.entity.enumfolder;

// [설정 - 언어 설정] SETTINGS-02: 서비스에서 지원하는 언어.
// User.preferredLanguage("기본 서비스 언어")의 값이자, 게시글/댓글 AI 번역(TranslateService)의
// 대상 언어로도 그대로 재사용된다 - UI 언어랑 번역 언어를 따로 두지 않는다.
public enum Language {
	KO, JA, EN;

	// TranslateService가 Gemini 프롬프트("다음 글을 자연스러운 OOO로 번역해줘")를 조립할 때 쓰는 한국어 언어 이름
	public String displayNameKo() {
		return switch (this) {
			case KO -> "한국어";
			case JA -> "일본어";
			case EN -> "영어";
		};
	}
}
