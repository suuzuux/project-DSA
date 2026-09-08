package megane6.weplanet.repository;

import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.AuthProvider;
import megane6.weplanet.domain.entity.enumfolder.Role;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // CHAT-06 AI팬 계정처럼, DB가 초기화돼도 항상 같은 이름으로 찾을 수 있어야 하는 경우 사용
    Optional<User> findByUsername(String username);
    List<User> findByUsernameIn(List<String> usernames);
    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);
    boolean existsByNickname(String nickname);
    boolean existsByEmail(String email);

    // DM 인박스(CHAT: 여러 아티스트 목록) 에서, 아직 대화 안 나눈 아티스트도 "추천" 칸에 보여주기 위해
    // 시스템에 있는 아티스트 전체 목록이 필요함
    List<User> findByRole(Role role);

    // 소셜 로그인 시 "이 provider의 이 providerId로 가입된 계정이 이미 있는지" 조회
    Optional<User> findByProviderAndProviderId(AuthProvider provider, String providerId);

    List<User> findByRoleAndAgency_Id(Role role, Long agencyId);

    @EntityGraph(attributePaths = "agency")
    Optional<User> findOneById(Long id);
}
