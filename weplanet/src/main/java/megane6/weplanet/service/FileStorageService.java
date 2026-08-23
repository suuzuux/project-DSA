package megane6.weplanet.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

// 업로드된 파일을 프로젝트 폴더 바로 아래 uploads/ 디렉토리에 실제로 저장/삭제하는 역할
@Slf4j
@Service
public class FileStorageService {

    private final Path uploadDir = Paths.get("uploads");

    // 서버 켜질 때 uploads 폴더가 없으면 미리 만들어둠
    public FileStorageService() {
        try {
            Files.createDirectories(uploadDir);
        } catch (IOException e) {
            throw new UncheckedIOException("업로드 폴더를 만들 수 없습니다.", e);
        }
    }

    // 파일을 저장하고, 서버 저장용 파일명(UUID + 확장자)을 돌려줌
    public String store(MultipartFile file) {
        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf("."));
        }

        String storedName = UUID.randomUUID() + ext;

        try {
            Files.copy(file.getInputStream(), uploadDir.resolve(storedName), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("파일 저장에 실패했습니다: " + originalName, e);
        }

        return storedName;
    }

    // 게시글/첨부파일 삭제 시, 디스크에 남아있는 실제 파일도 같이 지움
    public void delete(String storedName) {
        try {
            Files.deleteIfExists(uploadDir.resolve(storedName));
        } catch (IOException e) {
            // 파일 하나 정리 실패했다고 전체 삭제 흐름을 막을 필요는 없어서 로그만 남김
            log.warn("첨부파일 삭제 실패: {}", storedName, e);
        }
    }
}
