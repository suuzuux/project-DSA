package megane6.weplanet.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BoardMediaDTO {

	private Long id;
	private Long groupId;
	private String title;
	private String content;
	private List<MultipartFile> files = new ArrayList<>();
}
