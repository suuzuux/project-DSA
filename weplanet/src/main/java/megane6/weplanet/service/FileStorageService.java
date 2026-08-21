package megane6.weplanet.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {

	String store(MultipartFile file);          // 저장 후 저장 파일명 반환
	Resource loadAsResource(String storedName); // 파일 읽기(미리보기/재생)
	void delete(String storedName);             // 파일 삭제
}
