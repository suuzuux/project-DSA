package megane6.weplanet.service.shop;

import megane6.weplanet.service.main.FileStorageService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Set;

@Service
public class LocalShopImageStorage implements ShopImageStorage {

	private static final Set<String> ALLOWED_TYPES = Set.of(
			"image/jpeg", "image/png", "image/webp", "image/gif"
	);

	private final FileStorageService fileStorageService;

	public LocalShopImageStorage(FileStorageService fileStorageService) {
		this.fileStorageService = fileStorageService;
	}

	@Override
	public String storeImage(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("error.goods.imageRequired");
		}
		String contentType = normalizeContentType(file.getContentType());
		if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
			if (!hasAllowedExtension(file.getOriginalFilename())) {
				throw new IllegalArgumentException("error.goods.imageTypeNotAllowed");
			}
		}
		return fileStorageService.store(file);
	}

	@Override
	public void delete(String storedName) {
		if (storedName == null || storedName.isBlank()) {
			return;
		}
		fileStorageService.delete(storedName);
	}

	private static String normalizeContentType(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		int sep = raw.indexOf(';');
		return (sep >= 0 ? raw.substring(0, sep) : raw).trim().toLowerCase(Locale.ROOT);
	}

	private static boolean hasAllowedExtension(String name) {
		if (name == null) {
			return false;
		}
		String lower = name.toLowerCase(Locale.ROOT);
		return lower.endsWith(".jpg") || lower.endsWith(".jpeg")
				|| lower.endsWith(".png") || lower.endsWith(".webp") || lower.endsWith(".gif");
	}
}
