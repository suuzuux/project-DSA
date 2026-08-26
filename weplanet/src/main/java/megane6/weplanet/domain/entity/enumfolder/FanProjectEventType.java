package megane6.weplanet.domain.entity.enumfolder;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 팬 프로젝트 이벤트 유형.
 * SettlementBank와 같은 방식으로 화면 표기용 한글명을 함께 들고 있는다.
 * (등록 폼의 select와 목록 카드가 같은 문구를 쓰도록)
 */
@Getter
@RequiredArgsConstructor
public enum FanProjectEventType {
    BIRTHDAY_CAFE("생일카페"),
    BILLBOARD("전광판"),
    CONCERT("콘서트"),
    ETC("기타");

    private final String displayName;
}
