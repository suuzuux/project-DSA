package megane6.weplanet.security;

import megane6.weplanet.domain.entity.enumfolder.AuthProvider;

import java.io.Serializable;

// 소셜 연동 교체 확인 전까지 보관하는 새 소셜 정보
public record PendingSocialLink(AuthProvider provider, String providerId, Long targetUserId) implements Serializable {
}
