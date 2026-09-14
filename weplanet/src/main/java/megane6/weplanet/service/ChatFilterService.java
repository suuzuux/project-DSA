package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.FilterKeyword;
import megane6.weplanet.domain.entity.enumfolder.AdminActionType;
import megane6.weplanet.domain.entity.enumfolder.AdminTargetType;
import megane6.weplanet.repository.FilterKeywordRepository;
import megane6.weplanet.service.admin.AdminActionLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatFilterService {
    
    private static final int MAX_KEYWORD_LENGTH = 50;
    
    private final FilterKeywordRepository filterKeywordRepository;
    private final AdminActionLogService adminActionLogService;
    
    // 채팅 메시지에 금칙어가 포함되어 있는지 검사
    public boolean containsBannedWord(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        
        String normalizedContent =
                content.toLowerCase(Locale.ROOT);
        
        List<String> keywords =
                filterKeywordRepository.findAllKeywords();
        
        for (String keyword : keywords) {
            if (keyword == null || keyword.isBlank()) {
                continue;
            }
            
            if (normalizedContent.contains(
                    keyword.toLowerCase(Locale.ROOT)
            )) {
                return true;
            }
        }
        
        return false;
    }
    
    // 관리자 금칙어 목록
    public List<FilterKeyword> getAllKeywords() {
        return filterKeywordRepository
                .findAllByOrderByKeywordAsc();
    }
    
    // 금칙어 등록
    @Transactional
    public void addKeyword(
            String keyword,
            Long adminId,
            String ipAddress
    ) {
        String normalizedKeyword =
                normalizeKeyword(keyword);
        
        if (filterKeywordRepository
                .existsByKeywordIgnoreCase(normalizedKeyword)) {
            
            throw new IllegalArgumentException(
                    "이미 등록된 금칙어입니다."
            );
        }
        
        FilterKeyword filterKeyword =
                FilterKeyword.builder()
                        .keyword(normalizedKeyword)
                        .build();
        
        FilterKeyword savedKeyword =
                filterKeywordRepository.save(filterKeyword);
        
        adminActionLogService.recordAction(
                adminId,
                AdminActionType.KEYWORD_CREATE,
                AdminTargetType.KEYWORD,
                savedKeyword.getId(),
                "금칙어 등록: " + normalizedKeyword,
                ipAddress
        );
    }
    
    // 금칙어 수정
    @Transactional
    public void updateKeyword(
            Long id,
            String keyword,
            Long adminId,
            String ipAddress
    ) {
        FilterKeyword filterKeyword =
                filterKeywordRepository.findById(id)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "금칙어 정보를 찾을 수 없습니다."
                                )
                        );
        
        String normalizedKeyword =
                normalizeKeyword(keyword);
        
        if (filterKeywordRepository
                .existsByKeywordIgnoreCaseAndIdNot(
                        normalizedKeyword,
                        id
                )) {
            
            throw new IllegalArgumentException(
                    "이미 등록된 금칙어입니다."
            );
        }
        
        String previousKeyword =
                filterKeyword.getKeyword();
        
        filterKeyword.setKeyword(normalizedKeyword);
        
        adminActionLogService.recordAction(
                adminId,
                AdminActionType.KEYWORD_UPDATE,
                AdminTargetType.KEYWORD,
                id,
                "금칙어 수정: "
                        + previousKeyword
                        + " → "
                        + normalizedKeyword,
                ipAddress
        );
    }
    
    // 금칙어 삭제
    @Transactional
    public void deleteKeyword(
            Long id,
            Long adminId,
            String ipAddress
    ) {
        FilterKeyword filterKeyword =
                filterKeywordRepository.findById(id)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "금칙어 정보를 찾을 수 없습니다."
                                )
                        );
        
        String deletedKeyword =
                filterKeyword.getKeyword();
        
        filterKeywordRepository.delete(filterKeyword);
        
        adminActionLogService.recordAction(
                adminId,
                AdminActionType.KEYWORD_DELETE,
                AdminTargetType.KEYWORD,
                id,
                "금칙어 삭제: " + deletedKeyword,
                ipAddress
        );
    }
    
    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new IllegalArgumentException(
                    "금칙어를 입력해주세요."
            );
        }
        
        String normalizedKeyword = keyword.trim();
        
        if (normalizedKeyword.length()
                > MAX_KEYWORD_LENGTH) {
            
            throw new IllegalArgumentException(
                    "금칙어는 50자 이하로 입력해주세요."
            );
        }
        
        return normalizedKeyword;
    }
}