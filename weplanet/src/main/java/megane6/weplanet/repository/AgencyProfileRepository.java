package megane6.weplanet.repository;

import megane6.weplanet.domain.entity.AgencyProfile;
import megane6.weplanet.domain.entity.enumfolder.AgencyStatus;
import megane6.weplanet.domain.entity.enumfolder.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AgencyProfileRepository extends JpaRepository<AgencyProfile, Long> {

	Optional<AgencyProfile> findByUser_Id(Long userId);
	long countByApprovedAtIsNull();
	long countByApprovedAtIsNotNull();
	
	@Query("""
        select profile
        from AgencyProfile profile
        join fetch profile.user user
        join fetch profile.agency agency
        left join fetch profile.approvedBy approver
        where (
              :approved is null
              or (
                  :approved = true
                  and profile.approvedAt is not null
              )
              or (
                  :approved = false
                  and profile.approvedAt is null
              )
          )
          and (
              :userStatus is null
              or user.status = :userStatus
          )
          and (
              :agencyStatus is null
              or agency.status = :agencyStatus
          )
          and (
              :keyword is null
              or lower(user.username)
                    like lower(concat('%', :keyword, '%'))
              or lower(user.nickname)
                    like lower(concat('%', :keyword, '%'))
              or lower(user.email)
                    like lower(concat('%', :keyword, '%'))
              or lower(agency.name)
                    like lower(concat('%', :keyword, '%'))
              or lower(coalesce(agency.businessNo, ''))
                    like lower(concat('%', :keyword, '%'))
          )
        order by
          case
            when profile.approvedAt is null then 0
            else 1
          end asc,
          user.createdAt desc,
          user.id desc
        """)
	List<AgencyProfile> searchForAdmin(
			@Param("approved") Boolean approved,
			@Param("userStatus") UserStatus userStatus,
			@Param("agencyStatus") AgencyStatus agencyStatus,
			@Param("keyword") String keyword
	);
}
