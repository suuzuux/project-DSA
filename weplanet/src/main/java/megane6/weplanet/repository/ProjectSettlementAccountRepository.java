package megane6.weplanet.repository;

import megane6.weplanet.domain.entity.ProjectSettlementAccount;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectSettlementAccountRepository extends JpaRepository<ProjectSettlementAccount, Long> {
	
	@EntityGraph(attributePaths = "project")
	List<ProjectSettlementAccount> findByProject_IdIn(Collection<Long> projectIds);
	
	@EntityGraph(attributePaths = "project")
	Optional<ProjectSettlementAccount> findByProject_Id(Long projectId);
}
