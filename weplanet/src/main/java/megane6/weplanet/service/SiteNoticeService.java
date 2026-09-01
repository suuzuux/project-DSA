package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.SiteNotice;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.repository.SiteNoticeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class SiteNoticeService {

	private final SiteNoticeRepository siteNoticeRepository;

	@Transactional(readOnly = true)
	public List<SiteNotice> listAll() {
		return siteNoticeRepository.findAllByOrderByCreatedAtDesc();
	}

	@Transactional(readOnly = true)
	public List<SiteNotice> listPublished() {
		return siteNoticeRepository.findByPublishedTrueOrderByCreatedAtDesc();
	}

	@Transactional(readOnly = true)
	public SiteNotice get(Long noticeId) {
		return siteNoticeRepository.findById(noticeId)
				.orElseThrow(() -> new IllegalArgumentException("공지를 찾을 수 없습니다."));
	}

	@Transactional(readOnly = true)
	public SiteNotice getPublished(Long noticeId) {
		SiteNotice notice = get(noticeId);
		if (!notice.isPublished()) {
			throw new IllegalArgumentException("공지를 찾을 수 없습니다.");
		}
		return notice;
	}

	public SiteNotice save(User author, Long noticeId, String title, String content, boolean published) {
		if (title == null || title.isBlank()) {
			throw new IllegalArgumentException("제목을 입력해주세요.");
		}
		if (content == null || content.isBlank()) {
			throw new IllegalArgumentException("본문을 입력해주세요.");
		}
		if (noticeId == null) {
			return siteNoticeRepository.save(SiteNotice.create(author, title, content, published));
		}
		SiteNotice notice = get(noticeId);
		notice.update(title, content, published);
		return notice;
	}

	public void delete(Long noticeId) {
		siteNoticeRepository.delete(get(noticeId));
	}
}
