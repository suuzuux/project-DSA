package megane6.weplanet.service.event;

import java.util.regex.Pattern;

/** 본문에 해시태그가 태그로 들어 있는지 판정 (대소문자 무시, 앞뒤 태그 글자 불허, 마크다운 이스케이프 제거). */
public final class HashtagMatcher {
	
	// 에디터가 붙이는 역슬래시 이스케이프 제거 (\# → #)
	private static final Pattern MARKDOWN_ESCAPE = Pattern.compile("\\\\(\\p{Punct})");
	
	// 태그 글자 (한글·영문·숫자·밑줄)
	private static final String TAG_CHAR = "[\\p{L}\\p{N}_]";
	
	private HashtagMatcher() {
		// 유틸 클래스
	}
	
	public static boolean contains(String content, String hashtag) {
		if (content == null || hashtag == null || hashtag.isBlank()) {
			return false;
		}
		
		String plain = MARKDOWN_ESCAPE.matcher(content).replaceAll("$1");
		
		// 앞뒤에 태그 글자가 붙지 않은 태그만 찾는다 (Pattern.quote 로 글자 그대로).
		Pattern tagPattern = Pattern.compile(
				"(?<!" + TAG_CHAR + ")" + Pattern.quote(hashtag) + "(?!" + TAG_CHAR + ")",
				Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
		);
		
		return tagPattern.matcher(plain).find();
	}
}


