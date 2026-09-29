package megane6.weplanet.service.event;

import java.util.regex.Pattern;

/**
 * 게시글 본문에 해시태그가 "태그로서" 들어 있는지 판정한다.
 *   - 대소문자 무시           : #가을총공_stella 도 #가을총공_STELLA 로 인정
 *   - 앞뒤가 태그 글자면 불인정 : #가을총공_STELLA2 , 응원#가을총공_STELLA 는 다른 태그로 본다
 *   - 마크다운 이스케이프 제거  : 에디터가 \#가을총공\_STELLA 처럼 저장해도 인정
 */
public final class HashtagMatcher {
	
	// 마크다운 에디터(Toast UI)가 # _ * 같은 기호 앞에 붙이는 역슬래시를 떼어냄 (\# → #)
	private static final Pattern MARKDOWN_ESCAPE = Pattern.compile("\\\\(\\p{Punct})");
	
	// 해시태그를 이루는 글자 (HashtagEventTarget 의 규칙과 같음: 한글·영문·숫자·밑줄)
	private static final String TAG_CHAR = "[\\p{L}\\p{N}_]";
	
	private HashtagMatcher() {
		// static 메서드만 쓰는 유틸 클래스라, 객체 만들 일 X
	}
	
	public static boolean contains(String content, String hashtag) {
		if (content == null || hashtag == null || hashtag.isBlank()) {
			return false;
		}
		
		String plain = MARKDOWN_ESCAPE.matcher(content).replaceAll("$1");
		
		// (?<!태그글자) 태그 (?!태그글자) : 태그 바로 앞뒤에 태그 글자가 붙어 있으면 안 된다
		// Pattern.quote : 태그 안의 글자를 정규식 기호가 아니라 글자 그대로 찾게 한다
		Pattern tagPattern = Pattern.compile(
				"(?<!" + TAG_CHAR + ")" + Pattern.quote(hashtag) + "(?!" + TAG_CHAR + ")",
				Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
		);
		
		return tagPattern.matcher(plain).find();
	}
}


