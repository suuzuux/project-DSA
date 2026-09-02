package megane6.weplanet.security;

import megane6.weplanet.domain.entity.enumfolder.AuthProvider;

import java.io.Serializable;

// 회원가입 화면에서 소셜 로그인(구글 등) 시도했는데 같은 이메일의 기존 계정이 있어서
// 바로 합치지 않고 사용자에게 "연동할지" 확인받아야 할 때, 확인 화면으로 넘어가기 전까지
// 구글이 알려준 정보를 세션에 잠깐 담아두는 용도.
public record PendingSocialSignup(AuthProvider provider, String providerId, String email, String name) implements Serializable {
}
