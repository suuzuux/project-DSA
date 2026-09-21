package megane6.weplanet.service.shop;

import org.springframework.web.multipart.MultipartFile;

/**
 * 굿즈 썸네일 저장. 로컬 구현을 S3 등으로 교체할 때 이 인터페이스만 바꾸면 된다.
 */
public interface ShopImageStorage {

	String storeImage(MultipartFile file);

	void delete(String storedName);
}
