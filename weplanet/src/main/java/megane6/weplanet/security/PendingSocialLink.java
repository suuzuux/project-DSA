package megane6.weplanet.security;

import megane6.weplanet.domain.entity.enumfolder.AuthProvider;

import java.io.Serializable;

// AUTH-10 신설: 설정 화면에서 "연결하기"를 시도했는데 이미 다른 소셜 계정이 연동돼 있어서
// "연동 계정을 바꾸시겠습니까?" 확인을 받아야 할 때, 확인 화면으로 넘어가기 전까지
// 새로 연동하려는 소셜 계정 정보를 세션에 잠깐 담아두는 용도.
public record PendingSocialLink(AuthProvider provider, String providerId, Long targetUserId) implements Serializable {
}
