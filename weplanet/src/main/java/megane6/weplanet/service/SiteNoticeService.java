package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.SiteNotice;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.NoticeCategory;
import megane6.weplanet.repository.SiteNoticeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class SiteNoticeService {
	
	public static final int MAX_PINNED = 3;
	private final SiteNoticeRepository siteNoticeRepository;

	@Transactional(readOnly = true)
	public PageResult<SiteNotice> listAll(
			NoticeCategory category,
			String keyword,
			int page,
			int size
	) {
		String trimmedKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
		List<SiteNotice> all = siteNoticeRepository.search(category, trimmedKeyword);
		
		int fromIndex = Math.min(page * size, all.size());
		int toIndex = Math.min(fromIndex + size, all.size());
		List<SiteNotice> pageContent = all.subList(fromIndex, toIndex);
		
		return new PageResult<>(pageContent, page, size, all.size());
	}
	
	@Transactional(readOnly = true)
	public List<SiteNotice> listPublished() {
		return listPublished(null);
	}
	
	@Transactional(readOnly = true)
	public List<SiteNotice> listPublished(NoticeCategory category) {
		return siteNoticeRepository.findVisible(category);
	}

	@Transactional(readOnly = true)
	public SiteNotice get(Long noticeId) {
		return siteNoticeRepository.findById(noticeId)
				.orElseThrow(() -> new IllegalArgumentException("공지를 찾을 수 없습니다."));
	}

	@Transactional(readOnly = true)
	public SiteNotice getPublished(Long noticeId) {
		SiteNotice notice = get(noticeId);
		if (!notice.isVisible()) {
			throw new IllegalArgumentException("공지를 찾을 수 없습니다.");
		}
		return notice;
	}
	
	public SiteNotice save(
			User author,
			Long noticeId,
			NoticeCategory category,
			String title,
			String content,
			boolean published,
			LocalDateTime publishAt,
			boolean pinned) {
		if (title == null || title.isBlank()) {
			throw new IllegalArgumentException("제목을 입력해주세요.");
		}
		if (content == null || content.isBlank()) {
			throw new IllegalArgumentException("본문을 입력해주세요.");
		}
		SiteNotice notice = noticeId == null
				? SiteNotice.create(author, category, title, content, published, publishAt)
				: get(noticeId);
		if (noticeId != null) {
			notice.update(category, title, content, published, publishAt);
		}
		
		applyPinState(notice, pinned);
		return siteNoticeRepository.save(notice);
	}
	
	public void reorderPinned(List<Long> ids) {
		List<SiteNotice> pinned =
				siteNoticeRepository.findByPinnedTrueOrderByPinOrderAsc();
		if (ids == null || ids.isEmpty() || ids.size() != pinned.size()) {
			throw new IllegalArgumentException("상단 노출 공지 순서가 올바르지 않습니다.");
		}
		Map<Long, SiteNotice> byId = pinned.stream()
				.collect(Collectors.toMap(SiteNotice::getId, item -> item));
		List<SiteNotice> reordered = new ArrayList<>(ids.size());
		int order = 1;
		for (Long id : ids) {
			SiteNotice notice = byId.remove(id);
			if (notice == null) {
				throw new IllegalArgumentException("상단 노출 공지 순서가 올바르지 않습니다.");
			}
			notice.applyPin(true, order++);
			reordered.add(notice);
		}
		if (!byId.isEmpty()) {
			throw new IllegalArgumentException("상단 노출 공지 순서가 올바르지 않습니다.");
		}
		siteNoticeRepository.saveAll(reordered);
	}
	
	@Transactional(readOnly = true)
	public long countPinned() {
		return siteNoticeRepository.countByPinnedTrue();
	}
	
	private void applyPinState(SiteNotice notice, boolean pinned) {
		boolean wasPinned = notice.isPinned();
		if (pinned) {
			long count = siteNoticeRepository.countByPinnedTrue();
			if (!wasPinned && count >= MAX_PINNED) {
				throw new IllegalArgumentException("상단 노출은 최대 "
					+ MAX_PINNED + "개까지 가능합니다.");
			}
			if (!wasPinned) {
				List<SiteNotice> current =
						siteNoticeRepository.findByPinnedTrueOrderByPinOrderAsc();
				int shift = 2;
				for (SiteNotice item : current) {
					item.applyPin(true, shift++);
				}
				notice.applyPin(true, 1);
			}
			return;
		}
		if (wasPinned) {
			notice.applyPin(false, null);
			compactPinOrder();
		}
	}
	
	private void compactPinOrder() {
		List<SiteNotice> pinned =
				siteNoticeRepository.findByPinnedTrueOrderByPinOrderAsc();
		int order = 1;
		for (SiteNotice item : pinned) {
			item.applyPin(true, order++);
		}
		if (!pinned.isEmpty()) {
			siteNoticeRepository.saveAll(pinned);
		}
	}

	public void delete(Long noticeId) {
		siteNoticeRepository.delete(get(noticeId));
	}
	
	public record PageResult<T>(List<T> content, int page, int size, long totalElements) {
		public int totalPages() {
			return size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
		}
		public boolean hasPrevious() {
			return page > 0;
		}
		public boolean hasNext() {
			return (page + 1) < totalPages();
		}
	}
}
