package megane6.weplanet.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// 금칙어 (게시글·댓글·채팅 저장 전 검사, ChatFilterService).
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
