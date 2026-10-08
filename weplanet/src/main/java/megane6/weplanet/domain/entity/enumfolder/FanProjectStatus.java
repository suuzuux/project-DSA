package megane6.weplanet.domain.entity.enumfolder;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 팬 프로젝트 진행 상태 (등록 시 PENDING_APPROVAL, 관리자가 승인·반려). */
@Getter
@RequiredArgsConstructor
public enum FanProjectStatus {
    PENDING_APPROVAL("승인대기", "pending", false),
    APPROVED("승인완료", "approved", true),
    REJECTED("반려", "rejected", false),
    FUNDING("모금중", "funding", true),
    FUNDING_CLOSED("모금마감", "closed", true),
    COMPLETED("완료", "completed", true),
    CANCELLED("취소", "cancelled", false);

    private final String displayName;

    // project.css 의 .project-badge--{code} 와 짝
    private final String badgeCode;

    // 일반 목록에 공개할 수 있는 상태인지
    private final boolean publiclyVisible;

    /** 화면 표시용 메시지 키 (displayName 은 폴백) */
    public String getMessageKey() {
        return "project.status." + name();
    }
}
