package megane6.weplanet.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 와이어프레임 10번(급상승 커뮤니티 데뷔일)에서 씀.
// MediaGroupDataInitializer가 서버 시작 시 아티스트 User마다 하나씩 시딩해둠 (id == 아티스트 User.id)
@Entity
@Table(name = "artist_groups")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ArtistGroup {

    @Id
    private Long id;

    @Column(name = "agency_id", nullable = false)
    private Long agencyId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "debut_date")
    private LocalDate debutDate;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
