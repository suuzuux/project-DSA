package megane6.weplanet.service.main;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

/** 업로드 파일을 디스크(uploads 폴더)에 저장·삭제한다 (DB 에는 파일 정보만 저장). */
@Slf4j
@Service
public class FileStorageService {

    // 업로드 폴더 (프로젝트 폴더 아래 uploads)
    private final Path uploadDir = Paths.get("uploads");

    // uploads 폴더가 없으면 만든다.
    public FileStorageService() {
        try {
            Files.createDirectories(uploadDir);
        } catch (IOException e) {
            // 폴더를 만들 수 없으면 서버 시작을 막는다.
            throw new UncheckedIOException("업로드 폴더를 만들 수 없습니다.", e);
        }
    }

    /** UUID 파일명으로 저장하고 저장 파일명을 돌려준다 (원래 이름은 DB 에 기록). */
    public String store(MultipartFile file) {
        String originalName = file.getOriginalFilename();

        // 확장자는 저장 파일명에도 붙인다.
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf("."));
        }

        String storedName = UUID.randomUUID() + ext;

        try {
            // 업로드 내용을 저장 파일로 복사한다.
            Files.copy(file.getInputStream(), uploadDir.resolve(storedName), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("파일 저장에 실패했습니다: " + originalName, e);
        }

        return storedName;
    }

    // 이미지 전용 저장 (크기·Content-Type·매직바이트 확인, 확장자는 서버가 결정해 저장형 XSS 방지).
    public static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;

    public String storeImage(MultipartFile file) {
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("error.upload.imageTooLarge");
        }
        String contentType = file.getContentType();
        String ext = contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("image/")
                ? detectImageExtension(file)
                : null;
        if (ext == null) {
            throw new IllegalArgumentException("error.upload.imageTypeInvalid");
        }

        String storedName = UUID.randomUUID() + ext;
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, uploadDir.resolve(storedName), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("파일 저장에 실패했습니다: " + file.getOriginalFilename(), e);
        }
        return storedName;
    }

    // 앞 12바이트로 실제 이미지 형식을 판별한다.
    private static String detectImageExtension(MultipartFile file) {
        byte[] h = new byte[12];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.readNBytes(h, 0, h.length);
        } catch (IOException e) {
            return null;
        }
        if (read >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) {
            return ".jpg";
        }
        if (read >= 8 && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G'
                && h[4] == 0x0D && h[5] == 0x0A && h[6] == 0x1A && h[7] == 0x0A) {
            return ".png";
        }
        if (read >= 6 && h[0] == 'G' && h[1] == 'I' && h[2] == 'F' && h[3] == '8'
                && (h[4] == '7' || h[4] == '9') && h[5] == 'a') {
            return ".gif";
        }
        if (read >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') {
            return ".webp";
        }
        return null;
    }

    // 디스크의 파일을 삭제한다.
    public void delete(String storedName) {
        try {
            Files.deleteIfExists(uploadDir.resolve(storedName));
        } catch (IOException e) {
            // 파일 삭제 실패는 경고 로그만 남긴다.
            log.warn("첨부파일 삭제 실패: {}", storedName, e);
        }
    }
}
