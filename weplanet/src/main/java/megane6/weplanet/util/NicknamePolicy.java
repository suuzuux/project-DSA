package megane6.weplanet.util;

public class NicknamePolicy {
	
	private static final int MIN_LENGTH = 2;
	private static final int MAX_LENGTH = 12;
	private static final int REPEAT_THRESHOLD = 4; // 같은 문자가 이 횟수 이상 연속되면 거부
	
	private NicknamePolicy() {
	}
	
	public static boolean isAllowed(String nickname) {
		if (nickname == null) {
			return false;
		}
		String trimmed = nickname.trim();
		if (trimmed.length() < MIN_LENGTH || trimmed.length() > MAX_LENGTH) {
			return false;
		}
		if (!containsLetter(trimmed)) {
			return false;
		}
		return !hasRepeatedChars(trimmed, REPEAT_THRESHOLD);
	}
	
	private static boolean containsLetter(String s) {
		for (char c : s.toCharArray()) {
			if (Character.isLetter(c)) {
				return true;
			}
		}
		return false;
	}
	
	private static boolean hasRepeatedChars(String s, int threshold) {
		int count = 1;
		for (int i = 1; i < s.length(); i++) {
			if (s.charAt(i) == s.charAt(i - 1)) {
				count++;
				if (count >= threshold) {
					return true;
				}
			} else {
				count = 1;
			}
		}
		return false;
	}
}