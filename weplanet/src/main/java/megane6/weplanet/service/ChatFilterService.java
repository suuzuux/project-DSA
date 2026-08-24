package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.FilterKeyword;
import megane6.weplanet.repository.FilterKeywordRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatFilterService {

    private final FilterKeywordRepository filterKeywordRepository;

    // 메시지 내용에 금칙어가 하나라도 포함되어 있으면 true
    public boolean containsBannedWord(String content) {
        List<String> keywords = filterKeywordRepository.findAllKeywords();

        for (String keyword : keywords) {
            if (content.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    // 금칙어 목록 전체 조회 (관리 화면용)
    public List<FilterKeyword> getAllKeywords() {
        return filterKeywordRepository.findAll();
    }

    // 금칙어 등록 - 관리자만 호출 가능 (권한 체크는 컨트롤러에서)
    public void addKeyword(String keyword) {
        filterKeywordRepository.save(FilterKeyword.builder().keyword(keyword).build());
    }

    // 금칙어 삭제
    public void deleteKeyword(Long id) {
        filterKeywordRepository.deleteById(id);
    }
}