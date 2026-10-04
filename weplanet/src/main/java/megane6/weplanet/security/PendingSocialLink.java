package megane6.weplanet.security;

import megane6.weplanet.domain.entity.enumfolder.AuthProvider;

import java.io.Serializable;

// 이미 다른 소셜이 연동된 계정에서 "연결하기"를 눌렀을 때, "연동 계정을 바꾸시겠습니까?" 확인 전까지
// 새로 연동하려는 소셜 계정 정보를 세션에 잠깐 담아 둔다.
public record PendingSocialLink(AuthProvider provider, String providerId, Long targetUserId) implements Serializable {
}
