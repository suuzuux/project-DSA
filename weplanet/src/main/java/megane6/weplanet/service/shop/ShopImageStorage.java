package megane6.weplanet.service.shop;

import org.springframework.web.multipart.MultipartFile;

/** 굿즈 썸네일 저장 인터페이스 (저장소 교체 시 구현만 바꾼다). */
public interface ShopImageStorage {

	String storeImage(MultipartFile file);

	void delete(String storedName);
}
