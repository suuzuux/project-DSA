package megane6.weplanet.util;

import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** 마크다운 본문을 목록 미리보기 글로 바꾼다 (기호만 제거, 줄 수 제한은 CSS). */
public final class MarkdownPreview {

	// 미리보기 최대 길이
	private static final int MAX_LENGTH = 600;

	private static final Pattern CODE_FENCE = Pattern.compile("(?m)^\\s*(```|~~~).*$");
	private static final Pattern IMAGE = Pattern.compile("!\\[[^\\]]*]\\([^)]*\\)");
	private static final Pattern LINK = Pattern.compile("\\[([^\\]]*)]\\([^)]*\\)");
	private static final Pattern HTML_BREAK = Pattern.compile("(?i)<br\\s*/?>");
	private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
	private static final Pattern HEADING = Pattern.compile("(?m)^\\s{0,3}#{1,6}\\s+");	// "# 제목" (해시태그는 유지)
	private static final Pattern QUOTE = Pattern.compile("(?m)^\\s*(>\\s?)+");
	private static final Pattern LIST_MARK = Pattern.compile("(?m)^\\s*([-*+]|\\d+[.)])\\s+(\\[[ xX]]\\s+)?");
	private static final Pattern HR = Pattern.compile("(?m)^\\s*([-*_]\\s*){3,}$");
	private static final Pattern STRONG_STRIKE = Pattern.compile("(\\*\\*|__|~~)");
	private static final Pattern EMPHASIS = Pattern.compile("(?<![*\\w])\\*(?!\\s)([^*\\n]+?)(?<!\\s)\\*(?!\\*)");
	private static final Pattern INLINE_CODE = Pattern.compile("`");
	private static final Pattern TABLE_PIPE = Pattern.compile("\\s*\\|\\s*");
	private static final Pattern TABLE_DIVIDER = Pattern.compile("^[-:\\s]+$");
	private static final Pattern ESCAPE = Pattern.compile("\\\\([\\\\`*_{}\\[\\]()#+\\-.!>~|])");

	private MarkdownPreview() {
	}

	public static String of(String markdown) {
		if (markdown == null || markdown.isBlank()) {
			return "";
		}
		String text = markdown.replace("\r\n", "\n").replace('\r', '\n');
		text = CODE_FENCE.matcher(text).replaceAll("");
		text = IMAGE.matcher(text).replaceAll("");
		text = LINK.matcher(text).replaceAll("$1");
		text = HTML_BREAK.matcher(text).replaceAll("\n");
		text = HTML_TAG.matcher(text).replaceAll("");
		text = HR.matcher(text).replaceAll("");
		text = HEADING.matcher(text).replaceAll("");
		text = QUOTE.matcher(text).replaceAll("");
		text = LIST_MARK.matcher(text).replaceAll("");
		text = STRONG_STRIKE.matcher(text).replaceAll("");
		text = EMPHASIS.matcher(text).replaceAll("$1");
		text = INLINE_CODE.matcher(text).replaceAll("");
		text = ESCAPE.matcher(text).replaceAll("$1");

		// 빈 줄은 하나로 합친다.
		String joined = text.lines()
				.map(line -> TABLE_PIPE.matcher(line).replaceAll(" ").strip())
				.filter(line -> !line.isEmpty())
				.filter(line -> !TABLE_DIVIDER.matcher(line).matches())	// 표의 |---|---| 구분줄
				.collect(Collectors.joining("\n"));
		return joined.length() > MAX_LENGTH ? joined.substring(0, MAX_LENGTH) : joined;
	}
}
