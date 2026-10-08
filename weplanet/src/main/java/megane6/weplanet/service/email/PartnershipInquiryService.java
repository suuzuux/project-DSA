package megane6.weplanet.service.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.PartnershipApplication;
import megane6.weplanet.i18n.PreferredLocaleResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class PartnershipInquiryService {
    
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern(
                    "yyyy-MM-dd HH:mm:ss"
            );
    
    private final JavaMailSender mailSender;
    private final MessageSource messageSource;
    
    @Value(
            "${weplanet.partnership.recipient:admin4.wp@gmail.com}"
    )
    private String recipient;
    
    @Value(
            "${weplanet.base-url:http://localhost:9999}"
    )
    private String baseUrl;
    
    /** 새 신청 접수를 관리자에게 알린다. */
    public void sendNewApplicationNotice(
            PartnershipApplication application
    ) {
        SimpleMailMessage message =
                new SimpleMailMessage();
        
        message.setTo(recipient);
        message.setReplyTo(application.getEmail());
        message.setSubject(
                "[WePlaNet 등록신청] "
                        + application.getApplicantName()
        );
        message.setText(
                buildNewApplicationBody(application)
        );
        
        mailSender.send(message);
        
        log.info(
                "입점 신청 관리자 알림 발송: applicationId={}, to={}",
                application.getId(),
                recipient
        );
    }
    
    /** 승인 결과와 계정 활성화 링크를 신청자에게 알린다. */
    public void sendApprovalNotice(
            PartnershipApplication application,
            String username,
            String verificationKey,
            String rawToken,
            LocalDateTime expiresAt
    ) {
        SimpleMailMessage message = new SimpleMailMessage();
        
        message.setTo(application.getEmail());
        message.setReplyTo(recipient);
        // 신청 당시 화면 언어로 보낸다.
        Locale locale = applicantLocale(application);
        message.setSubject(
                messageSource.getMessage("mail.partnershipApproval.subject", null, locale)
        );
        message.setText(
                buildApprovalBody(
                        application,
                        username,
                        buildActivationUrl(verificationKey, rawToken),
                        expiresAt,
                        locale
                )
        );
        mailSender.send(message);
        
        log.info("입점 신청 승인 메일 발송: applicationId={}", application.getId());
    }
    
    private String buildActivationUrl(
            String verificationKey,
            String rawToken
    ) {
        return baseUrl
                + "/partner/activate?key="
                + verificationKey
                + "&token="
                + rawToken;
    }
    
    /** 반려 결과와 사유를 신청자에게 알린다. */
    
    public void sendRejectionNotice(
            PartnershipApplication application
    ) {
        SimpleMailMessage message =
                new SimpleMailMessage();
        
        message.setTo(application.getEmail());
        message.setReplyTo(recipient);
        Locale locale = applicantLocale(application);
        message.setSubject(
                messageSource.getMessage("mail.partnershipRejection.subject", null, locale)
        );
        message.setText(
                buildRejectionBody(application, locale)
        );
        
        mailSender.send(message);
        
        log.info(
                "입점 신청 반려 메일 발송: applicationId={}",
                application.getId()
        );
    }
    
    private String buildNewApplicationBody(
            PartnershipApplication application
    ) {
        return """
                WePlaNet 아티스트·소속사 등록 신청이 접수되었습니다.

                ■ 신청 번호   : %d
                ■ 신청 유형   : %s
                ■ 신청자명    : %s
                ■ 담당자      : %s
                ■ 회신 이메일 : %s
                ■ 연락처      : %s
                ■ 접수 일시   : %s

                ■ 신청 내용
                %s

                ---
                관리자 페이지에서 신청 내용을 확인한 뒤
                승인 또는 반려 처리해주세요.
                """
                .formatted(
                        application.getId(),
                        application
                                .getApplicantType()
                                .getDisplayName(),
                        application.getApplicantName(),
                        application.getContactName(),
                        application.getEmail(),
                        blankToDash(application.getPhone()),
                        formatTime(
                                application.getCreatedAt()
                        ),
                        application.getMessage()
                );
    }
    
    private String buildApprovalBody(
            PartnershipApplication application,
            String username,
            String activationUrl,
            LocalDateTime expiresAt,
            Locale locale
    ) {
        // 숫자 쉼표가 붙지 않게 신청 번호는 String 으로 넘긴다.
        return messageSource.getMessage(
                "mail.partnershipApproval.body",
                new Object[]{
                        application.getContactName(),
                        applicantTypeLabel(application, locale),
                        String.valueOf(application.getId()),
                        formatTime(application.getReviewedAt()),
                        username,
                        activationUrl,
                        formatTime(expiresAt)
                },
                locale
        );
    }
    
    private String buildRejectionBody(
            PartnershipApplication application,
            Locale locale
    ) {
        return messageSource.getMessage(
                "mail.partnershipRejection.body",
                new Object[]{
                        application.getContactName(),
                        applicantTypeLabel(application, locale),
                        String.valueOf(application.getId()),
                        application.getRejectionReason(),
                        formatTime(application.getReviewedAt())
                },
                locale
        );
    }
    
    private Locale applicantLocale(PartnershipApplication application) {
        return PreferredLocaleResolver.toLocale(application.getApplicantLanguage());
    }
    
    // 신청자 유형도 받는 사람 언어로
    private String applicantTypeLabel(PartnershipApplication application, Locale locale) {
        return messageSource.getMessage(application.getApplicantType().getMessageKey(), null, locale);
    }
    
    private String formatTime(LocalDateTime value) {
        if (value == null) {
            return "-";
        }
        
        return value.format(TIME_FORMAT);
    }
    
    private String blankToDash(String value) {
        return value == null || value.isBlank()
                ? "-"
                : value;
    }
}