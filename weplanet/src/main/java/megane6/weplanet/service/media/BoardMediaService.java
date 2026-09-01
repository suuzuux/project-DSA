package megane6.weplanet.service.media;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.dto.media.BoardMediaFileViewDTO;
import megane6.weplanet.domain.dto.media.BoardMediaViewDTO;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.media.BoardMediaEntity;
import megane6.weplanet.domain.entity.media.BoardMediaFileEntity;
import megane6.weplanet.domain.entity.media.BoardMediaLike;
import megane6.weplanet.repository.media.BoardMediaFileRepository;
import megane6.weplanet.repository.media.BoardMediaLikeRepository;
import megane6.weplanet.repository.media.BoardMediaRepository;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class BoardMediaService {

    private final BoardMediaRepository boardMediaRepository;
    private final BoardMediaFileRepository boardMediaFileRepository;
    private final BoardMediaLikeRepository boardMediaLikeRepository;
    private final FileStorageService fileStorageService; // 기존에 쓰던 파일 저장 서비스

    // 허용하는 파일 형식(MIME)
    private static final List<String> ALLOWED_TYPES = Arrays.asList(
            "image/jpeg", "image/png", "image/gif", "image/webp",
            "video/mp4", "video/webm", "video/quicktime"
    );

    // ── 저장(업로드) : 게시글 + 파일 여러 개를 한 번에 저장 ──
    public Long create(Long groupId, Long uploaderId, String title, String content,
                       List<MultipartFile> files) {

        LocalDateTime now = LocalDateTime.now();

        // 1) 게시글 먼저 만든다
        BoardMediaEntity post = BoardMediaEntity.builder()
                .groupId(groupId)
                .uploaderId(uploaderId)
                .title(title)
                .content(content)
                .createdAt(now)
                .updatedAt(now)
                .build();

        // 2) 파일들을 하나씩 저장하고 게시글에 붙인다
        int order = 0;
        if (files != null) {
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) {
                    continue; // 빈 칸은 건너뜀
                }
                String contentType = file.getContentType();
                if (!ALLOWED_TYPES.contains(contentType)) {
                    throw new IllegalArgumentException("허용되지 않는 파일 형식입니다: " + contentType);
                }

                String storedName = fileStorageService.store(file); // 디스크에 저장

                BoardMediaFileEntity fileEntity = BoardMediaFileEntity.builder()
                        .originalName(file.getOriginalFilename())
                        .storedName(storedName)
                        .contentType(contentType)
                        .mediaType(contentType.startsWith("image/") ? "IMAGE" : "VIDEO")
                        .fileSize(file.getSize())
                        .sortOrder(order)
                        .createdAt(now)
                        .build();

                post.addFile(fileEntity);
                order++;
            }
        }

        if (post.getFiles().isEmpty()) {
            throw new IllegalArgumentException("파일을 최소 1개 첨부해 주세요.");
        }

        boardMediaRepository.save(post); // cascade 로 파일도 함께 저장
        return post.getId();
    }

    // ── 수정 : 제목/내용 변경 (+ 새 파일이 오면 뒤에 추가) ──
    public void edit(Long id, Long communityGroupId, String title, String content, List<MultipartFile> files) {
        BoardMediaEntity post = getActivePostInCommunity(id, communityGroupId);
        post.setTitle(title);
        post.setContent(content);
        post.setUpdatedAt(LocalDateTime.now());

        if (files != null) {
            LocalDateTime now = LocalDateTime.now();
            int order = post.getFiles().size(); // 기존 파일 뒤부터 순서 매김
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) {
                    continue;
                }
                String contentType = file.getContentType();
                if (!ALLOWED_TYPES.contains(contentType)) {
                    throw new IllegalArgumentException("허용되지 않는 파일 형식입니다: " + contentType);
                }
                String storedName = fileStorageService.store(file);

                BoardMediaFileEntity fileEntity = BoardMediaFileEntity.builder()
                        .originalName(file.getOriginalFilename())
                        .storedName(storedName)
                        .contentType(contentType)
                        .mediaType(contentType.startsWith("image/") ? "IMAGE" : "VIDEO")
                        .fileSize(file.getSize())
                        .sortOrder(order)
                        .createdAt(now)
                        .build();

                post.addFile(fileEntity);
                order++;
            }
        }
        // @Transactional 안에서 값만 바꿔도 자동 저장되지만, 명확하게 저장 호출
        boardMediaRepository.save(post);
    }

    // ── 삭제 : 소프트 삭제(기록/파일은 남기고 목록에서만 숨김) ──
    public void softDelete(Long id, Long communityGroupId) {
        BoardMediaEntity post = getActivePostInCommunity(id, communityGroupId);
        post.setDeletedAt(LocalDateTime.now());
        boardMediaRepository.save(post);
    }

    @Transactional(readOnly = true)
    public BoardMediaViewDTO getInCommunity(Long id, Long groupId) {
        BoardMediaEntity post = getActivePost(id);
        if (!post.getGroupId().equals(groupId)) {
            throw new IllegalArgumentException("이 커뮤니티의 미디어가 아닙니다.");
        }
        return toViewDTO(post);
    }

    // ── 목록 조회 : 엔티티 → 화면용 DTO 로 변환 ──
    @Transactional(readOnly = true)
    public List<BoardMediaViewDTO> list(Long groupId) {
        List<BoardMediaEntity> posts =
                boardMediaRepository.findByGroupIdAndDeletedAtIsNullOrderByCreatedAtDesc(groupId);

        List<BoardMediaViewDTO> result = new ArrayList<>();
        for (BoardMediaEntity post : posts) {
            result.add(toViewDTO(post));
        }
        return result;
    }

    // ── 파일 서빙 : 화면에서 이미지/영상을 불러올 때 ──
    @Transactional(readOnly = true)
    public BoardMediaFileEntity getFile(Long fileId) {
        return boardMediaFileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("파일을 찾을 수 없습니다: " + fileId));
    }

    public Resource loadResource(BoardMediaFileEntity file) {
        return fileStorageService.loadAsResource(file.getStoredName());
    }

    public boolean toggleLike(Long mediaId, User user) {
        BoardMediaEntity post = getActivePost(mediaId);
        Optional<BoardMediaLike> existing = boardMediaLikeRepository.findByBoardAndUser(post, user);
        if (existing.isPresent()) {
            boardMediaLikeRepository.delete(existing.get());
            post.setLikeCount(Math.max(0, post.getLikeCount() - 1));
            boardMediaRepository.save(post);
            return false;
        }
        boardMediaLikeRepository.save(BoardMediaLike.builder()
                .board(post)
                .user(user)
                .build());
        post.setLikeCount(post.getLikeCount() + 1);
        boardMediaRepository.save(post);
        return true;
    }

    @Transactional(readOnly = true)
    public int getLikeCount(Long mediaId) {
        return getActivePost(mediaId).getLikeCount();
    }

    // ── 내부 헬퍼 ──
    private BoardMediaEntity getActivePost(Long id) {
        return boardMediaRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다: " + id));
    }

    private BoardMediaEntity getActivePostInCommunity(Long id, Long communityGroupId) {
        BoardMediaEntity post = getActivePost(id);
        if (!post.getGroupId().equals(communityGroupId)) {
            throw new IllegalStateException("다른 커뮤니티의 미디어는 수정/삭제할 수 없습니다.");
        }
        return post;
    }

    private BoardMediaViewDTO toViewDTO(BoardMediaEntity post) {
        List<BoardMediaFileViewDTO> fileViews = post.getFiles().stream()
                .map(file -> BoardMediaFileViewDTO.builder()
                        .id(file.getId())
                        .mediaType(file.getMediaType())
                        .originalName(file.getOriginalName())
                        .contentType(file.getContentType())
                        .build())
                .toList();

        return BoardMediaViewDTO.builder()
                .id(post.getId())
                .groupId(post.getGroupId())
                .uploaderId(post.getUploaderId())
                .title(post.getTitle())
                .content(post.getContent())
                .createdAt(post.getCreatedAt())
                .fileCount(fileViews.size())
                .likeCount(post.getLikeCount())
                .files(fileViews)
                .build();
    }
}
