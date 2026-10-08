package megane6.weplanet.domain.entity;
import megane6.weplanet.service.artist.ArtistMemberService;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import megane6.weplanet.domain.entity.enumfolder.GroupGender;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 아티스트 그룹(커뮤니티)별 정보 - 데뷔일과 커뮤니티 탐색 필터 (id == 아티스트 User.id).
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

    @Column(name = "name_en", length = 100)
    private String nameEn;

    @Column(name = "fandom_name", length = 50)
    private String fandomName;

    @Column(name = "debut_date")
    private LocalDate debutDate;

    @Column(nullable = false, length = 20)
    private String status;

    // 커뮤니티 탐색 필터

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 10)
    private GroupGender gender;

    @Column(name = "member_count")
    private Integer memberCount;

    @Column(name = "nationality", length = 50)
    private String nationality;

    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 변경 감지 UPDATE 때 수정 시각을 갱신한다.
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
