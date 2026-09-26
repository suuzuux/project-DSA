package megane6.weplanet.domain.entity.enumfolder;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 팬 프로젝트 참여 결제 상태 (토스 가상계좌 기준 흐름)
 *
 * READY               : 참여하기 누름 -> 주문만 만들어진 상태 (결제창 열기 전)
 * WAITING_FOR_DEPOSIT : 가상계좌 발급 완료, 입금 기다리는 중
 * PAID                : 입금 확인됨 (이때부터 모금액에 집계)
 * FAILED              : 결제창/승인 단계에서 실패, 또는 결제창을 닫아 방치됨
 * EXPIRED             : 입금기한 지남
 * CANCELLED / REFUND_REQUESTED / REFUNDED : 취소·환불 (다음 작업)
 *
 * displayName : 화면에 보여줄 한글 이름
 * badgeCode   : collection.css 의 .project-history-card__status--{badgeCode} 와 짝
 */
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
}