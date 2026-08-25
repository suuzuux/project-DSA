package megane6.weplanet.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import megane6.weplanet.domain.entity.enumfolder.FanProjectEligibilityRule;
import megane6.weplanet.domain.entity.enumfolder.FanProjectEventType;
import megane6.weplanet.domain.entity.enumfolder.FanProjectStatus;
import megane6.weplanet.domain.entity.enumfolder.IdentityVerificationMethod;
import megane6.weplanet.domain.entity.enumfolder.Role;

import java.time.LocalDateTime;

@Entity
@Table(name = "fan_project")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Project {

    public static final long MIN_GOAL_AMOUNT = 10_000L;
    public static final long MAX_GOAL_AMOUNT = 3_000_000L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 팀 공통 커뮤니티와 동일하게 users.id(ARTIST)를 프로젝트 대상 아티스트로 사용한다.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "artist_id", nullable = false)
    private User artist;

    // username이 아닌 users.id(PK)가 creator_id에 저장된다.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @Column(nullable = false, length = 20)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private FanProjectEventType eventType;

    @Column(name = "goal_amount", nullable = false)
    private Long goalAmount;

    @Column(name = "funding_start_at", nullable = false)
    private LocalDateTime fundingStartAt;

    @Column(name = "funding_end_at", nullable = false)
    private LocalDateTime fundingEndAt;

    @Column(nullable = false, length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FanProjectStatus status;

    @Column(name = "special_badge_count_at_apply", nullable = false)
    private Integer specialBadgeCountAtApply;

    @Column(name = "basic_badge_count_at_apply", nullable = false)
    private Integer basicBadgeCountAtApply;

    @Enumerated(EnumType.STRING)
    @Column(name = "eligibility_rule_code", nullable = false, length = 50)
    private FanProjectEligibilityRule eligibilityRuleCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "identity_verification_method", nullable = false, length = 20)
    private IdentityVerificationMethod identityVerificationMethod;

    @Column(name = "identity_verified_at", nullable = false)
    private LocalDateTime identityVerifiedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    private Project(
            User artist,
            User creator,
            String title,
            FanProjectEventType eventType,
            Long goalAmount,
            LocalDateTime fundingStartAt,
            LocalDateTime fundingEndAt,
            String description,
            int specialBadgeCount,
            int basicBadgeCount,
            LocalDateTime identityVerifiedAt
    ) {
        validateCreation(
                artist,
                creator,
                title,
                eventType,
                goalAmount,
                fundingStartAt,
                fundingEndAt,
                description,
                specialBadgeCount,
                basicBadgeCount,
                identityVerifiedAt
        );

        this.artist = artist;
        this.creator = creator;
        this.title = title;
        this.eventType = eventType;
        this.goalAmount = goalAmount;
        this.fundingStartAt = fundingStartAt;
        this.fundingEndAt = fundingEndAt;
        this.description = description;
        this.status = FanProjectStatus.PENDING_APPROVAL;
        this.specialBadgeCountAtApply = specialBadgeCount;
        this.basicBadgeCountAtApply = basicBadgeCount;
        this.eligibilityRuleCode = FanProjectEligibilityRule.SPECIAL_1_AND_BASIC_5;
        this.identityVerificationMethod = IdentityVerificationMethod.PHONE;
        this.identityVerifiedAt = identityVerifiedAt;
    }

    public static Project createPending(
            User artist,
            User creator,
            String title,
            FanProjectEventType eventType,
            Long goalAmount,
            LocalDateTime fundingStartAt,
            LocalDateTime fundingEndAt,
            String description,
            int specialBadgeCount,
            int basicBadgeCount,
            LocalDateTime identityVerifiedAt
    ) {
        return new Project(
                artist,
                creator,
                title,
                eventType,
                goalAmount,
                fundingStartAt,
                fundingEndAt,
                description,
                specialBadgeCount,
                basicBadgeCount,
                identityVerifiedAt
        );
    }

    private static void validateCreation(
            User artist,
            User creator,
            String title,
            FanProjectEventType eventType,
            Long goalAmount,
            LocalDateTime fundingStartAt,
            LocalDateTime fundingEndAt,
            String description,
            int specialBadgeCount,
            int basicBadgeCount,
            LocalDateTime identityVerifiedAt
    ) {
        if (artist == null || artist.getRole() != Role.ARTIST || creator == null) {
            throw new IllegalArgumentException("아티스트와 프로젝트 개설자는 필수입니다.");
        }
        if (title == null || title.isBlank() || title.length() > 20) {
            throw new IllegalArgumentException("프로젝트 제목은 1자 이상 20자 이하로 입력해야 합니다.");
        }
        if (eventType == null) {
            throw new IllegalArgumentException("이벤트 유형을 선택해야 합니다.");
        }
        if (goalAmount == null || goalAmount < MIN_GOAL_AMOUNT || goalAmount > MAX_GOAL_AMOUNT) {
            throw new IllegalArgumentException("목표 금액은 10,000원 이상 3,000,000원 이하여야 합니다.");
        }
        if (fundingStartAt == null || fundingEndAt == null || !fundingEndAt.isAfter(fundingStartAt)) {
            throw new IllegalArgumentException("모금 마감일은 모금 시작일보다 이후여야 합니다.");
        }
        if (description == null || description.isBlank() || description.length() > 1000) {
            throw new IllegalArgumentException("프로젝트 상세 설명은 1자 이상 1,000자 이하로 입력해야 합니다.");
        }
        if (specialBadgeCount < 1 || basicBadgeCount < 5) {
            throw new IllegalStateException("프로젝트 개설에는 스페셜 뱃지 1개와 기본 뱃지 5개 이상이 필요합니다.");
        }
        if (identityVerifiedAt == null) {
            throw new IllegalStateException("휴대폰 본인인증을 완료해야 합니다.");
        }
    }

    @PrePersist
    private void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    private void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
