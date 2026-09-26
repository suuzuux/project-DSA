package megane6.weplanet.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.PartnershipApplication;
import megane6.weplanet.domain.entity.enumfolder.PartnershipApplicantType;
import megane6.weplanet.service.PartnershipApplicationService;
import megane6.weplanet.service.email.PartnershipInquiryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class PartnershipInquiryController {
    
    private static final String SESSION_KEY_LAST_SENT =
            "PARTNERSHIP_LAST_SENT_AT";
    
    private static final long COOLDOWN_SECONDS = 60;
    
    private final PartnershipApplicationService applicationService;
    private final PartnershipInquiryService inquiryEmailService;
    
    @GetMapping("/partnership")
    public String form() {
        return "partnership";
    }
    
    @PostMapping("/partnership")
    public String submit(
            @RequestParam(required = false)
            String applicantType,
            
            @RequestParam(required = false)
            String applicantName,
            
            @RequestParam(required = false)
            String contactName,
            
            @RequestParam(required = false)
            String email,
            
            @RequestParam(required = false)
            String phone,
            
            @RequestParam(required = false)
            String message,
            
            HttpSession session,
            Model model
    ) {
        model.addAttribute(
                "form",
                formValues(
                        applicantType,
                        applicantName,
                        contactName,
                        email,
                        phone,
                        message
                )
        );
        
        PartnershipApplicantType parsedApplicantType;
        
        try {
            parsedApplicantType =
                    parseApplicantType(applicantType);
        } catch (IllegalArgumentException e) {
            model.addAttribute(
                    "errorMessage",
                    e.getMessage()
            );
            return "partnership";
        }
        
        if (isOnCooldown(session)) {
            model.addAttribute(
                    "errorMessage",
                    "방금 신청이 접수되었습니다. 잠시 후 다시 시도해주세요."
            );
            return "partnership";
        }
        
        PartnershipApplication application;
        
        try {
            application = applicationService.submit(
                    parsedApplicantType,
                    applicantName,
                    contactName,
                    email,
                    phone,
                    message
            );
        } catch (IllegalArgumentException e) {
            model.addAttribute(
                    "errorMessage",
                    e.getMessage()
            );
            return "partnership";
        } catch (Exception e) {
            log.error(
                    "입점 신청 DB 저장 실패: applicantName={}",
                    applicantName,
                    e
            );
            
            model.addAttribute(
                    "errorMessage",
                    "지금은 신청을 접수할 수 없습니다. 잠시 후 다시 시도해주세요."
            );
            return "partnership";
        }
        
        /*
         * DB 저장과 이메일 발송을 분리한다.
         *
         * 메일 서버가 잠시 고장 나더라도 이미 접수된 신청이
         * 롤백되거나 사용자에게 실패로 표시되면 안 된다.
         */
        try {
            inquiryEmailService.sendNewApplicationNotice(
                    application
            );
        } catch (Exception e) {
            log.warn(
                    "입점 신청 관리자 알림 메일 발송 실패: applicationId={}",
                    application.getId(),
                    e
            );
        }
        
        session.setAttribute(
                SESSION_KEY_LAST_SENT,
                LocalDateTime.now()
        );
        
        return "redirect:/partnership?submitted";
    }
    
    private PartnershipApplicantType parseApplicantType(
            String value
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "신청 유형을 선택해주세요."
            );
        }
        
        try {
            return PartnershipApplicantType.valueOf(
                    value.trim().toUpperCase(Locale.ROOT)
            );
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "올바른 신청 유형을 선택해주세요."
            );
        }
    }
    
    private boolean isOnCooldown(HttpSession session) {
        Object lastSent =
                session.getAttribute(SESSION_KEY_LAST_SENT);
        
        if (!(lastSent instanceof LocalDateTime sentAt)) {
            return false;
        }
        
        return Duration.between(
                sentAt,
                LocalDateTime.now()
        ).getSeconds() < COOLDOWN_SECONDS;
    }
    
    private Map<String, String> formValues(
            String applicantType,
            String applicantName,
            String contactName,
            String email,
            String phone,
            String message
    ) {
        Map<String, String> values = new HashMap<>();
        
        values.put(
                "applicantType",
                nullToEmpty(applicantType)
        );
        values.put(
                "applicantName",
                nullToEmpty(applicantName)
        );
        values.put(
                "contactName",
                nullToEmpty(contactName)
        );
        values.put(
                "email",
                nullToEmpty(email)
        );
        values.put(
                "phone",
                nullToEmpty(phone)
        );
        values.put(
                "message",
                nullToEmpty(message)
        );
        
        return values;
    }
    
    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}