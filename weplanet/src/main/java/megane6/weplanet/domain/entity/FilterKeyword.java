package megane6.weplanet.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// 채팅에서 사용을 금지할 단어(금칙어) 하나. 관리자가 등록/삭제하고, 채팅 메시지를 저장하기 전에
// 이 목록에 있는 단어가 포함돼 있는지 검사하는 데 쓰임 (ChatFilterService 참고)
@Entity
@Table(name = "filter_keyword")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FilterKeyword {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String keyword;
}
