package megane6.weplanet.repository;

import jakarta.persistence.LockModeType;
import megane6.weplanet.domain.entity.Goods;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.GoodsStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GoodsRepository extends JpaRepository<Goods, Long> {

	List<Goods> findByArtistAndDeletedAtIsNullOrderBySortOrderAscIdAsc(User artist);

	List<Goods> findByArtist_IdAndStatusAndDeletedAtIsNullOrderBySortOrderAscIdAsc(Long artistId, GoodsStatus status);

	List<Goods> findByStatusAndDeletedAtIsNullOrderBySortOrderAscIdAsc(GoodsStatus status);

	Optional<Goods> findByIdAndArtistAndDeletedAtIsNull(Long id, User artist);

	Optional<Goods> findByIdAndDeletedAtIsNull(Long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			SELECT g FROM Goods g
			WHERE g.id = :id AND g.deletedAt IS NULL
			""")
	Optional<Goods> findByIdAndDeletedAtIsNullForUpdate(@Param("id") Long id);
}
