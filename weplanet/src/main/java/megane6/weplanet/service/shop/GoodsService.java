package megane6.weplanet.service.shop;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.Goods;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.GoodsStatus;
import megane6.weplanet.repository.GoodsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class GoodsService {

	private final GoodsRepository goodsRepository;
	private final ShopImageStorage shopImageStorage;

	@Transactional(readOnly = true)
	public List<Goods> listForArtist(User artist) {
		return goodsRepository.findByArtistAndDeletedAtIsNullOrderBySortOrderAscIdAsc(artist);
	}

	@Transactional(readOnly = true)
	public Goods getOwned(User artist, Long goodsId) {
		return goodsRepository.findByIdAndArtistAndDeletedAtIsNull(goodsId, artist)
				.orElseThrow(() -> new IllegalArgumentException("굿즈를 찾을 수 없습니다."));
	}

	public Goods create(User artist,
						String name,
						String description,
						int price,
						String officialUrl,
						GoodsStatus status,
						MultipartFile thumbnail) {
		requireArtist(artist);
		String stored = shopImageStorage.storeImage(thumbnail);
		List<Goods> existing = listForArtist(artist);
		int nextOrder = existing.isEmpty()
				? 0
				: existing.stream().mapToInt(Goods::getSortOrder).max().orElse(-1) + 1;
		Goods goods = Goods.builder()
				.artist(artist)
				.name(name.trim())
				.description(blankToNull(description))
				.price(price)
				.thumbnailUrl(stored)
				.officialUrl(blankToNull(officialUrl))
				.status(status != null ? status : GoodsStatus.ON_SALE)
				.sortOrder(nextOrder)
				.build();
		return goodsRepository.save(goods);
	}

	public Goods update(User artist,
						Long goodsId,
						String name,
						String description,
						int price,
						String officialUrl,
						GoodsStatus status,
						MultipartFile thumbnail) {
		Goods goods = getOwned(artist, goodsId);
		goods.setName(name.trim());
		goods.setDescription(blankToNull(description));
		goods.setPrice(price);
		goods.setOfficialUrl(blankToNull(officialUrl));
		if (status != null) {
			goods.setStatus(status);
		}
		if (thumbnail != null && !thumbnail.isEmpty()) {
			String previous = goods.getThumbnailUrl();
			goods.setThumbnailUrl(shopImageStorage.storeImage(thumbnail));
			shopImageStorage.delete(previous);
		}
		return goodsRepository.save(goods);
	}

	public void softDelete(User artist, Long goodsId) {
		Goods goods = getOwned(artist, goodsId);
		goods.softDelete();
		goodsRepository.save(goods);
	}

	/** direction: "up" = 목록에서 한 칸 위(sortOrder 감소), "down" = 한 칸 아래 */
	public void move(User artist, Long goodsId, String direction) {
		List<Goods> list = listForArtist(artist);
		int index = -1;
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i).getId().equals(goodsId)) {
				index = i;
				break;
			}
		}
		if (index < 0) {
			throw new IllegalArgumentException("굿즈를 찾을 수 없습니다.");
		}
		int swapWith = "up".equals(direction) ? index - 1 : index + 1;
		if (swapWith < 0 || swapWith >= list.size()) {
			return;
		}
		Goods a = list.get(index);
		Goods b = list.get(swapWith);
		int tmp = a.getSortOrder();
		a.setSortOrder(b.getSortOrder());
		b.setSortOrder(tmp);
		goodsRepository.save(a);
		goodsRepository.save(b);
	}

	private static void requireArtist(User artist) {
		if (artist == null) {
			throw new IllegalArgumentException("아티스트를 선택해주세요.");
		}
	}

	private static String blankToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
