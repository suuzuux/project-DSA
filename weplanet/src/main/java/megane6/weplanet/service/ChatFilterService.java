package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
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
}