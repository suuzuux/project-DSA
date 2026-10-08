package megane6.weplanet.domain.dto.event;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 이벤트 등록·수정 폼 (artistIds[i] 와 hashtags[i] 가 같은 줄). */
@Getter
@Setter
@NoArgsConstructor
public class HashtagEventForm {
	
	private String title;
	
	// <input type="date"> 값을 ISO 날짜로 받는다.
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate startDate;
	
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate endDate;
	
	private List<Long> artistIds = new ArrayList<>();
	
	private List<String> hashtags =  new ArrayList<>();
	
	// i번째 해시태그 (줄 수가 안 맞아도 안전하게)
	public String hashtagAt(int index) {
		return index < hashtags.size() ? hashtags.get(index) : null;
	}
}
