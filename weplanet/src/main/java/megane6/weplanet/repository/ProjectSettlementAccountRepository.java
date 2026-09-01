package megane6.weplanet.repository;

import megane6.weplanet.domain.entity.ProjectSettlementAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectSettlementAccountRepository extends JpaRepository<ProjectSettlementAccount, Long> {
}
