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

import java.time.LocalDateTime;

@Getter
@Setter
public class ProjectRequestDTO {

    @NotNull(message = "아티스트를 선택해주세요.")
    private Long artistId;

    @NotBlank(message = "프로젝트 제목을 입력해주세요.")
    @Size(max = 20, message = "프로젝트 제목은 20자 이하로 입력해주세요.")
    private String title;

    @NotNull(message = "이벤트 유형을 선택해주세요.")
    private FanProjectEventType eventType;

    @NotNull(message = "대표 이미지를 등록해주세요.")
    private MultipartFile coverImage;

    @NotNull(message = "목표 금액을 입력해주세요.")
    @Min(value = 10_000, message = "목표 금액은 최소 10,000원이어야 합니다.")
    @Max(value = 3_000_000, message = "목표 금액은 최대 3,000,000원까지 가능합니다.")
    private Long goalAmount;

    @NotNull(message = "모금 시작일을 입력해주세요.")
    @FutureOrPresent(message = "모금 시작일은 현재 이후여야 합니다.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime fundingStartAt;

    @NotNull(message = "모금 마감일을 입력해주세요.")
    @Future(message = "모금 마감일은 현재 이후여야 합니다.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime fundingEndAt;

    @NotBlank(message = "프로젝트 상세 설명을 입력해주세요.")
    @Size(max = 1000, message = "상세 설명은 1,000자 이하로 입력해주세요.")
    private String description;

    // 등록 화면에서 본인인증에 사용하지만 fan_project에는 원문을 저장하지 않는다.
    @NotBlank(message = "휴대폰 번호를 입력해주세요.")
    @Pattern(
            regexp = "^01[016789][0-9]{7,8}$",
            message = "휴대폰 번호는 하이픈 없이 숫자만 입력해주세요."
    )
    private String phoneNumber;

    // select의 value는 enum 이름, 표시 문구는 SettlementBank.displayName을 사용한다.
    @NotNull(message = "정산 은행을 선택해주세요.")
    private SettlementBank settlementBank;

    // 예금주명은 로그인 회원의 본인인증 실명(User.realName)을 사용한다.
    @NotBlank(message = "계좌번호를 입력해주세요.")
    @Pattern(regexp = "^[0-9]{6,30}$", message = "계좌번호는 하이픈 없이 숫자만 입력해주세요.")
    private String accountNumber;

    @AssertTrue(message = "모금 마감일은 모금 시작일보다 이후여야 합니다.")
    public boolean isFundingPeriodValid() {
        return fundingStartAt == null
                || fundingEndAt == null
                || fundingEndAt.isAfter(fundingStartAt);
    }
}
