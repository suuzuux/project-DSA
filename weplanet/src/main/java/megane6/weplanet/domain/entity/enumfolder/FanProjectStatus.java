package megane6.weplanet.domain.entity.enumfolder;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 팬 프로젝트 진행 상태.
 * <p>
 * 등록하면 PENDING_APPROVAL로 시작하고, 관리자가 APPROVED/REJECTED로 바꾼다.
 * 목록 카드에 상태 뱃지를 띄우기 위해 한글명과 CSS 색상 구분용 코드를 함께 들고 있는다.
 */
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

    // project.css의 .project-badge--{code} 와 짝을 이루는 값
    private final String badgeCode;

    // 비로그인 사용자와 다른 팬의 일반 목록에 공개할 수 있는 상태인지
    private final boolean publiclyVisible;

    /**
     * SETTINGS-03 커밋3: displayName(한국어)은 관리자 화면 등에서 쓰는 레거시 폴백으로 남기고,
     * 사용자 화면은 이 키로 messages*.properties 문구를 찾는다(GoodsStatus 등과 같은 패턴).
     */
    public String getMessageKey() {
        return "project.status." + name();
    }
}
