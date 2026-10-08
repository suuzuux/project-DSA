package megane6.weplanet.domain.entity.enumfolder;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 팬 프로젝트 결제 상태 - READY → WAITING_FOR_DEPOSIT → PAID (실패 FAILED, 기한 만료 EXPIRED). */
@Getter
@RequiredArgsConstructor
public enum FanProjectPaymentStatus {
    READY("결제 대기", "ready"),
    WAITING_FOR_DEPOSIT("입금 대기", "waiting"),
    PAID("참여 완료", "paid"),
    FAILED("결제 실패", "closed"),
    EXPIRED("기한 만료", "closed"),
    CANCELLED("취소", "closed"),
    REFUND_REQUESTED("환불 요청", "waiting"),
    REFUNDED("환불 완료", "closed");
    
    private final String displayName;
    private final String badgeCode;

    // 화면 표시용 메시지 키 (displayName 은 폴백)
    public String getMessageKey() {
        return "project.paymentStatus." + name();
    }
}