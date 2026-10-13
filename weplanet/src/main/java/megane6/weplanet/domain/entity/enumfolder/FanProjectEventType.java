package megane6.weplanet.domain.entity.enumfolder;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 팬 프로젝트 이벤트 유형 (화면 표기용 한글명 포함). */
@Getter
@RequiredArgsConstructor
public enum FanProjectEventType {
    BIRTHDAY_CAFE("생일카페"),
    BILLBOARD("전광판"),
    CONCERT("콘서트"),
    ETC("기타");

    private final String displayName;

    /** 화면 표시용 메시지 키 (displayName 은 폴백) */
    public String getMessageKey() {
        return "project.eventType." + name();
    }
}
