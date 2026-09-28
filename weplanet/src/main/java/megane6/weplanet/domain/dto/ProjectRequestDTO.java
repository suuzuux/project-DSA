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

/**
 * SETTINGS-03 커밋3: 검증 메시지는 {project.validation.*} 키로 두고, Spring Boot 기본 검증기가
 * messages*.properties에서 현재 로케일 문구를 찾아 끼워 넣는다(MessageSourceMessageInterpolator).
 */
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

    // 화면에서는 날짜(년월일)만 받는다. 시각은 ProjectService에서 붙인다.
    // (시작일 00:00:00 / 마감일 23:59:59 - fan_project 컬럼은 그대로 DATETIME(6))
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

    // select의 value는 enum 이름, 표시 문구는 SettlementBank.displayName을 사용한다.
    @NotNull(message = "{project.validation.bankRequired}")
    private SettlementBank settlementBank;

    // 예금주명은 로그인 회원의 본인인증 실명(User.realName)을 사용한다.
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
