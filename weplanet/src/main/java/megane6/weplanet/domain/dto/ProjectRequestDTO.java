package megane6.weplanet.domain.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import megane6.weplanet.domain.entity.enumfolder.FanProjectEventType;
import megane6.weplanet.domain.entity.enumfolder.SettlementBank;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

/** 검증 메시지는 {project.validation.*} 키로 두고 현재 로케일 문구로 채운다. */
@Getter
@Setter
public class ProjectRequestDTO {

    @NotNull(message = "{project.validation.artistRequired}")
    private Long artistId;

    @NotBlank(message = "{project.validation.titleRequired}")
    @Size(max = 20, message = "{project.validation.titleTooLong}")
    private String title;

    @NotNull(message = "{project.validation.eventTypeRequired}")
    private FanProjectEventType eventType;
    
    private MultipartFile coverImage;

    @NotNull(message = "{project.validation.goalRequired}")
    @Min(value = 10_000, message = "{project.validation.goalMin}")
    @Max(value = 3_000_000, message = "{project.validation.goalMax}")
    private Long goalAmount;

    // 날짜만 받고 시각(00:00:00 / 23:59:59)은 서비스에서 붙인다.
    @NotNull(message = "{project.validation.startRequired}")
    @FutureOrPresent(message = "{project.validation.startNotPast}")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fundingStartAt;

    @NotNull(message = "{project.validation.endRequired}")
    @Future(message = "{project.validation.endFuture}")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fundingEndAt;

    @NotBlank(message = "{project.validation.descriptionRequired}")
    @Size(max = 1000, message = "{project.validation.descriptionTooLong}")
    private String description;
    
    @NotBlank(message = "{project.validation.emailVerificationRequired}")
    private String emailVerificationKey;

    // select 값은 enum 이름, 표시는 displayName
    @NotNull(message = "{project.validation.bankRequired}")
    private SettlementBank settlementBank;

    // 예금주명은 회원 실명을 쓴다.
    @NotBlank(message = "{project.validation.accountNumberRequired}")
    @Pattern(regexp = "^[0-9]{6,30}$", message = "{project.validation.accountNumberPattern}")
    private String accountNumber;
    
    @AssertTrue(message = "{project.validation.projectPolicyRequired}")
    private boolean projectPolicyAgreed;
    
    @AssertTrue(message = "{project.validation.settlementPolicyRequired}")
    private boolean settlementPolicyAgreed;
    
    @AssertTrue(message = "{project.validation.privacyPolicyRequired}")
    private boolean privacyPolicyAgreed;
    
    @AssertTrue(message = "{project.validation.endAfterStart}")
    public boolean isFundingPeriodValid() {
        return fundingStartAt == null
                || fundingEndAt == null
                || fundingEndAt.isAfter(fundingStartAt);
    }
}
