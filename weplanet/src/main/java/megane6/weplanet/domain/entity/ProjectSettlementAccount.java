package megane6.weplanet.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import megane6.weplanet.domain.entity.convert.SettlementBankConverter;
import megane6.weplanet.domain.entity.enumfolder.SettlementBank;
import megane6.weplanet.domain.entity.enumfolder.SettlementVerificationStatus;

import java.time.LocalDateTime;

@Entity
@Table(name = "fan_project_settlement_account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectSettlementAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, unique = true)
    private Project project;

    @Convert(converter = SettlementBankConverter.class)
    @Column(name = "bank_code", nullable = false, length = 3)
    private SettlementBank bank;

    // 평문이 아닌 실제 암호화 결과(byte[])만 저장한다.
    @Column(name = "account_number_enc", nullable = false, columnDefinition = "VARBINARY(512)")
    private byte[] accountNumberEncrypted;

    @Column(name = "account_number_hmac", nullable = false, length = 64)
    private String accountNumberHmac;

    @Column(name = "account_number_last4", nullable = false, length = 4)
    private String accountNumberLast4;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private SettlementVerificationStatus verificationStatus;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private ProjectSettlementAccount(
            Project project,
            SettlementBank bank,
            byte[] accountNumberEncrypted,
            String accountNumberHmac,
            String accountNumberLast4
    ) {
        this.project = project;
        this.bank = bank;
        this.accountNumberEncrypted = accountNumberEncrypted;
        this.accountNumberHmac = accountNumberHmac;
        this.accountNumberLast4 = accountNumberLast4;
        this.verificationStatus = SettlementVerificationStatus.UNVERIFIED;
    }

    public static ProjectSettlementAccount createUnverified(
            Project project,
            SettlementBank bank,
            byte[] accountNumberEncrypted,
            String accountNumberHmac,
            String accountNumberLast4
    ) {
        return new ProjectSettlementAccount(
                project,
                bank,
                accountNumberEncrypted,
                accountNumberHmac,
                accountNumberLast4
        );
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
