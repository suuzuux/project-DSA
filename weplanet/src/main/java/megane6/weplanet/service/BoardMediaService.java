package megane6.weplanet.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.dto.BoardMediaDTO;
import megane6.weplanet.domain.dto.BoardMediaViewDTO;
import megane6.weplanet.domain.entity.BoardMediaEntity;
import megane6.weplanet.domain.entity.BoardMediaFileEntity;
import megane6.weplanet.repository.BoardMediaFileRepository;
import megane6.weplanet.repository.BoardMediaRepository;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Slf4j
@RequiredArgsConstructor
@Transactional
@Service
public class BoardMediaService {
	private static final Set<String> ALLOWED = Set.of(
			"image/jpeg", "image/png", "image/gif", "image/webp",
			"video/mp4", "video/webm", "video/quicktime"
	);

	private final BoardMediaRepository boardRepository;
	private final BoardMediaFileRepository fileRepository;
	private final FileStorageService storage;

	/** 게시글 생성: 제목/내용 + 파일 여러 개를 한 게시글로 저장 */
	@org.springframework.transaction.annotation.Transactional
	public Long create(BoardMediaDTO dto, Long uploaderId) {
		List<MultipartFile> valid = validated(dto.getFiles());
		if (valid.isEmpty()) {
			throw new IllegalArgumentException("파일을 최소 1개 첨부해 주세요.");
		}
		LocalDateTime now = LocalDateTime.now();

		BoardMediaEntity post = new BoardMediaEntity();
		post.setGroupId(dto.getGroupId());
		post.setUploaderId(uploaderId);
		post.setTitle(dto.getTitle());
		post.setContent(dto.getContent());
		post.setCreatedAt(now);
		post.setUpdatedAt(now);

		int order = 0;
		for (MultipartFile f : valid) {
			post.addFile(toFileEntity(f, order++, now));
		}
		return boardRepository.save(post).getId(); // cascade 로 파일도 함께 저장
	}

	/** 편집: 제목/내용 수정 + 새 파일이 오면 뒤에 추가 */
	@org.springframework.transaction.annotation.Transactional
	public void edit(Long id, BoardMediaDTO dto) {
		BoardMediaEntity post = getActive(id);
		post.setTitle(dto.getTitle());
		post.setContent(dto.getContent());

		List<MultipartFile> valid = validated(dto.getFiles());
		if (!valid.isEmpty()) {
			int order = post.getFiles().stream()
					.mapToInt(BoardMediaFileEntity::getSortOrder).max().orElse(-1) + 1;
			LocalDateTime now = LocalDateTime.now();
			for (MultipartFile f : valid) {
				post.addFile(toFileEntity(f, order++, now));
			}
		}
		post.setUpdatedAt(LocalDateTime.now());
	}

	/** 첨부 1개만 삭제 (파일 교체/제거용) */
	@org.springframework.transaction.annotation.Transactional
	public void deleteFile(Long postId, Long fileId) {
		BoardMediaEntity post = getActive(postId);
		post.getFiles().removeIf(f -> {
			if (f.getId().equals(fileId)) {
				storage.delete(f.getStoredName()); // 디스크 파일 제거
				return true;                        // orphanRemoval 로 행 삭제
			}
			return false;
		});
	}

	/** 게시글 소프트 삭제 (기록/파일은 남기고 목록에서만 숨김) */
	@org.springframework.transaction.annotation.Transactional
	public void softDelete(Long id) {
		getActive(id).setDeletedAt(LocalDateTime.now());
	}

	/** 목록 (뷰 DTO 로 변환) */
	@org.springframework.transaction.annotation.Transactional(readOnly = true)
	public Page<BoardMediaViewDTO> list(Long groupId, int page, int size) {
		return boardRepository
				.findByGroupIdAndDeletedAtIsNullOrderByCreatedAtDesc(groupId, PageRequest.of(page, size))
				.map(BoardMediaViewDTO::from);
	}

	@org.springframework.transaction.annotation.Transactional(readOnly = true)
	public BoardMediaViewDTO getView(Long id) {
		return BoardMediaViewDTO.from(getActive(id));
	}

	// ── 파일 스트리밍 ──
	@org.springframework.transaction.annotation.Transactional(readOnly = true)
	public BoardMediaFileEntity getFile(Long fileId) {
		return fileRepository.findById(fileId)
				.orElseThrow(() -> new IllegalArgumentException("파일을 찾을 수 없습니다: " + fileId));
	}
	public Resource loadResource(BoardMediaFileEntity f) {
		return storage.loadAsResource(f.getStoredName());
	}

	// ── 내부 헬퍼 ──
	private BoardMediaEntity getActive(Long id) {
		return boardRepository.findByIdAndDeletedAtIsNull(id)
				.orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다: " + id));
	}

	/** 비어있지 않은 파일만 걸러 형식 검증 (저장 전에 전부 검사) */
	private List<MultipartFile> validated(List<MultipartFile> files) {
		List<MultipartFile> result = new ArrayList<>();
		if (files == null) return result;
		for (MultipartFile f : files) {
			if (f == null || f.isEmpty()) continue;
			String ct = f.getContentType();
			if (ct == null || !ALLOWED.contains(ct)) {
				throw new IllegalArgumentException("허용되지 않는 파일 형식입니다: " + ct);
			}
			result.add(f);
		}
		return result;
	}

	private BoardMediaFileEntity toFileEntity(MultipartFile f, int order, LocalDateTime now) {
		String ct = f.getContentType();
		BoardMediaFileEntity fe = new BoardMediaFileEntity();
		fe.setStoredName(storage.store(f));
		fe.setOriginalName(f.getOriginalFilename());
		fe.setContentType(ct);
		fe.setMediaType(Objects.requireNonNull(ct).startsWith("image/") ? "IMAGE" : "VIDEO");
		fe.setFileSize(f.getSize());
		fe.setSortOrder(order);
		fe.setCreatedAt(now);
		return fe;
	}
}
