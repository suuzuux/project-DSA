package megane6.weplanet.domain.dto.event;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 해시태그 총공 이벤트 등록/수정 폼 입력값.
 * 참여 아티스트는 폼의 같은 줄끼리 순서가 맞는다 (artistIds[0] ↔ hashtags[0]).
 * (input name="artistIds" / name="hashtags" 가 줄마다 하나씩 있으면 스프링이 List 로 모아준다)
 */
@Getter
@Setter
@NoArgsConstructor
public class HashtagEventForm {
	
	private String title;
	
	// <input type="date"> 는 "2026-10-10" 형식으로 보내므로 ISO 날짜로 받는다
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate startDate;
	
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate endDate;
	
	private List<Long> artistIds = new ArrayList<>();
	
	private List<String> hashtags =  new ArrayList<>();
	
	// i번째 줄의 해시태그 (줄 수가 안 맞게 들어와도 터지지 않게)
	public String hashtagAt(int index) {
		return index < hashtags.size() ? hashtags.get(index) : null;
	}
}
