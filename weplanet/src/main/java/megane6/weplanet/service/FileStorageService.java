package megane6.weplanet.service;

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

/**
 * 업로드된 파일을 "실제로" 서버 컴퓨터의 디스크에 저장/삭제하는 역할.
 * <p>
 * 지금까지 배운 DB 저장(JPA)은 "글자/숫자 같은 데이터"를 저장하는 거였다면,
 * 이 클래스는 이미지나 문서 같은 "파일 그 자체"를 컴퓨터의 폴더 안에 저장하는 걸 다룸.
 * DB에는 그 파일의 "이름표"(원본 파일명, 저장 경로 등)만 저장하고, 파일 내용물 자체는
 * 여기서 uploads 라는 폴더 안에 따로 저장해두는 구조.
 */
@Slf4j // log.warn(...) 처럼 로그를 남길 수 있게 해주는 롬복 어노테이션 (System.out.println 대신 씀)
@Service
public class FileStorageService {

    // 파일들을 저장할 폴더 경로 (프로젝트 폴더 바로 아래의 "uploads" 폴더)
    private final Path uploadDir = Paths.get("uploads");

    // 생성자 - 이 서비스 객체가 스프링에 의해 처음 만들어질 때(서버 켜질 때) 딱 한 번 실행됨.
    // uploads 폴더가 아직 없으면 미리 만들어두는 초기화 작업
    public FileStorageService() {
        try {
            Files.createDirectories(uploadDir);
        } catch (IOException e) {
            // 폴더 생성이 실패하면 이후 파일 저장 자체가 불가능하므로, 서버 시작을 막아버림
            throw new UncheckedIOException("업로드 폴더를 만들 수 없습니다.", e);
        }
    }

    /**
     * 업로드된 파일 하나를 디스크에 저장하고, 저장할 때 사용한 파일명을 돌려줌.
     * <p>
     * 원본 파일명을 그대로 쓰지 않는 이유:
     * - 같은 이름의 파일을 두 명이 올리면 서로 덮어써버림 (예: "사진.jpg"를 여러 명이 올리는 경우)
     * - 그래서 UUID(절대 겹치지 않는 랜덤 문자열)로 새 이름을 만들어서 저장하고,
     * 원래 이름은 DB(PostAttachment.originalName)에 따로 기록해서 화면엔 원래 이름으로 보여줌
     */
    public String store(MultipartFile file) {
        String originalName = file.getOriginalFilename();

        // 확장자(.jpg, .pdf 등)만 따로 뽑아냄 - 저장 파일명에도 확장자는 그대로 붙여줘야
        // 나중에 브라우저가 이미지인지 문서인지 등을 알아볼 수 있음
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf("."));
        }

        String storedName = UUID.randomUUID() + ext;

        try {
            // file.getInputStream() : 업로드된 파일의 내용물을 읽어올 수 있는 통로
            // Files.copy(...) : 그 내용물을 uploadDir 안의 storedName 이라는 새 파일로 그대로 복사해서 저장
            Files.copy(file.getInputStream(), uploadDir.resolve(storedName), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("파일 저장에 실패했습니다: " + originalName, e);
        }

        return storedName;
    }

    // AUTH-11: 프로필 사진·배경, 메인 배너처럼 "이미지만" 받아야 하는 곳에서 쓰는 저장 메서드.
    // store() 는 원본 확장자를 그대로 붙이기 때문에 .html/.svg 파일을 올리면 /uploads 에서 우리 사이트 주소로
    // 열려 스크립트가 실행될 수 있었다(저장형 XSS). 여기서는
    //  1) 크기 상한(10MB)
    //  2) 브라우저가 보낸 Content-Type 이 image/* 인지
    //  3) 파일 앞부분(매직바이트)이 실제로 jpg/png/gif/webp 인지
    // 를 모두 확인하고, 저장 확장자는 원본 파일명이 아니라 3)에서 판별한 형식으로 서버가 붙인다.
    public static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;

    public String storeImage(MultipartFile file) {
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("이미지는 10MB 이하만 올릴 수 있습니다.");
        }
        String contentType = file.getContentType();
        String ext = contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("image/")
                ? detectImageExtension(file)
                : null;
        if (ext == null) {
            throw new IllegalArgumentException("이미지 파일(jpg, png, gif, webp)만 올릴 수 있습니다.");
        }

        String storedName = UUID.randomUUID() + ext;
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, uploadDir.resolve(storedName), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("파일 저장에 실패했습니다: " + file.getOriginalFilename(), e);
        }
        return storedName;
    }

    // 파일 앞 12바이트로 실제 이미지 형식을 판별한다. 허용 형식이 아니면 null.
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

    // 게시글/첨부파일이 삭제될 때, 디스크에 실제로 남아있는 파일도 같이 지워서 정리함
    public void delete(String storedName) {
        try {
            Files.deleteIfExists(uploadDir.resolve(storedName));
        } catch (IOException e) {
            // 파일 하나 지우다가 실패했다고 해서 전체 삭제 작업(게시글 삭제 등)까지 막을 필요는 없으므로,
            // 예외를 던지지 않고 경고 로그만 남기고 넘어감
            log.warn("첨부파일 삭제 실패: {}", storedName, e);
        }
    }
}
