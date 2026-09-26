package megane6.weplanet.repository;

import megane6.weplanet.domain.entity.PartnershipApplication;
import megane6.weplanet.domain.entity.enumfolder.PartnershipApplicantType;
import megane6.weplanet.domain.entity.enumfolder.PartnershipApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PartnershipApplicationRepository extends JpaRepository<PartnershipApplication, Long> {
	
	@EntityGraph(attributePaths = "reviewedBy")
	@Query("""
        select application
        from PartnershipApplication application
        where (:status is null or application.status = :status)
          and (
              :applicantType is null
              or application.applicantType = :applicantType
          )
          and (
              :keyword is null
              or lower(application.applicantName)
                    like lower(concat('%', :keyword, '%'))
              or lower(application.contactName)
                    like lower(concat('%', :keyword, '%'))
              or lower(application.email)
                    like lower(concat('%', :keyword, '%'))
          )
        order by application.createdAt desc, application.id desc
        """)
	Page<PartnershipApplication> searchForAdmin(
			@Param("status") PartnershipApplicationStatus status,
			@Param("applicantType") PartnershipApplicantType applicationType,
			@Param("keyword") String keyword,
			Pageable pageable
	);
	
	@EntityGraph(attributePaths = "reviewedBy")
	@Query("""
        select application
        from PartnershipApplication application
        where application.id = :id
        """)
	Optional<PartnershipApplication> findDetailById(
			@Param("id") Long id
	);
	
	long countByStatus(PartnershipApplicationStatus status);
}
