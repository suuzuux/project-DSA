package megane6.weplanet.domain.entity;

/*
    게시판 종류 구분용 열거형
    FAN    : 팬 전용 게시판 (팬만 작성 가능)
    ARTIST : 아티스트 전용 게시판 (아티스트/관계자만 작성 가능)
 */
public enum BoardType {
    FAN,
    ARTIST
}