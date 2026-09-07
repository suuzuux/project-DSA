package megane6.weplanet.repository;

import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.AuthProvider;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.domain.entity.enumfolder.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // CHAT-06 AI팬 계정처럼, DB가 초기화돼도 항상 같은 이름으로 찾을 수 있어야 하는 경우 사용
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);
    boolean existsByNickname(String nickname);
    boolean existsByEmail(String email);

    // DM 인박스(CHAT: 여러 아티스트 목록) 에서, 아직 대화 안 나눈 아티스트도 "추천" 칸에 보여주기 위해
    // 시스템에 있는 아티스트 전체 목록이 필요함
    List<User> findByRole(Role role);
    
    long countByStatus(UserStatus status);
    
    List<User> findByStatus(UserStatus status);
    
    // 소셜 로그인 계정 조회
    Optional<User> findByProviderAndProviderId(
            AuthProvider provider,
            String providerId
    );

    List<User> findByRoleAndAgency_Id(Role role, Long agencyId);
    
    long countByRole(Role role);
    
    @Query("""
        select user
        from User user
        where user.role = :role
          and (:status is null or user.status = :status)
          and (
              :keyword is null
              or lower(user.nickname)
                    like lower(concat('%', :keyword, '%'))
              or lower(user.username)
                    like lower(concat('%', :keyword, '%'))
          )
        order by user.nickname asc
        """)
    List<User> searchByRole(
            @Param("role") Role role,
            @Param("status") UserStatus status,
            @Param("keyword") String keyword
    );
    
    @Query("""
        select user
        from User user
        left join fetch user.agency agency
        where (:role is null or user.role = :role)
          and (:status is null or user.status = :status)
          and (:provider is null or user.provider = :provider)
          and (
              :keyword is null
              or lower(user.username)
                    like lower(concat('%', :keyword, '%'))
              or lower(user.nickname)
                    like lower(concat('%', :keyword, '%'))
              or lower(user.email)
                    like lower(concat('%', :keyword, '%'))
              or lower(coalesce(agency.name, ''))
                    like lower(concat('%', :keyword, '%'))
          )
        order by user.createdAt desc, user.id desc
        """)
    List<User> searchForAdmin(
            @Param("role") Role role,
            @Param("status") UserStatus status,
            @Param("provider") AuthProvider provider,
            @Param("keyword") String keyword
    );
    
    long countByRoleAndStatus(Role role, UserStatus status);
}
