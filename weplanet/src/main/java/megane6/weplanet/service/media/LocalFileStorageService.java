package megane6.weplanet.service.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/** 로컬 디스크(./uploads)에 저장. 포트폴리오/개발용. */
@Service
public class LocalFileStorageService implements FileStorageService {

	private final Path root;

	public LocalFileStorageService(@Value("${app.upload-dir:./uploads}") String uploadDir) {
		this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
		try {
			Files.createDirectories(root);
		} catch (IOException e) {
			throw new IllegalStateException("업로드 폴더를 만들 수 없습니다: " + root, e);
		}
	}

	@Override
	public String store(MultipartFile file) {
		try {
			String ext = "";
			String orig = file.getOriginalFilename();
			if (orig != null && orig.contains(".")) {
				ext = orig.substring(orig.lastIndexOf('.')).toLowerCase();
			}
			String storedName = UUID.randomUUID() + ext;
			Path dest = root.resolve(storedName).normalize();
			if (!dest.getParent().equals(root)) {
				throw new IllegalArgumentException("잘못된 저장 경로입니다.");
			}
			try (InputStream in = file.getInputStream()) {
				Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
			}
			return storedName;
		} catch (IOException e) {
			throw new RuntimeException("파일 저장에 실패했습니다.", e);
		}
	}

	@Override
	public Resource loadAsResource(String storedName) {
		try {
			Path file = root.resolve(storedName).normalize();
			Resource resource = new UrlResource(file.toUri());
			if (resource.exists() && resource.isReadable()) return resource;
			throw new RuntimeException("파일을 찾을 수 없습니다: " + storedName);
		} catch (MalformedURLException e) {
			throw new RuntimeException("파일 경로가 올바르지 않습니다: " + storedName, e);
		}
	}

	@Override
	public void delete(String storedName) {
		try {
			Path file = root.resolve(storedName).normalize();
			if (file.getParent().equals(root)) {
				Files.deleteIfExists(file);
			}
		} catch (IOException e) {
			throw new RuntimeException("파일 삭제에 실패했습니다: " + storedName, e);
		}
	}
}
