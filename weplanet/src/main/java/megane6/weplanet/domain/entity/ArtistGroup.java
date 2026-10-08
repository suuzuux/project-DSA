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

// 와이어프레임 10번(급상승 커뮤니티 데뷔일)에서 씀.
// MediaGroupDataInitializer가 서버 시작 시 아티스트 User마다 하나씩 시딩해둠 (id == 아티스트 User.id)
// 테이블 간소화: 예전 artist_group_profiles(커뮤니티 탐색 필터)를 이 테이블에 합쳤다. 둘 다 그룹(커뮤니티) 1개당 1행이었다.
// (artist_profile 은 그룹 멤버 계정마다 1행이라 합치지 않았다)
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

    // ---- 커뮤니티 탐색 필터 (예전 artist_group_profiles) ----

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

    // 멤버 수 동기화(ArtistMemberService) 같은 변경 감지 UPDATE 때 수정 시각을 갱신한다
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
