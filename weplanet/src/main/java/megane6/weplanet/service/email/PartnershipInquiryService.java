package megane6.weplanet.service.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.PartnershipApplication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class PartnershipInquiryService {
    
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern(
                    "yyyy-MM-dd HH:mm:ss"
            );
    
    private final JavaMailSender mailSender;
    
    @Value(
            "${weplanet.partnership.recipient:admin4.wp@gmail.com}"
    )
    private String recipient;
    
    @Value(
            "${weplanet.base-url:http://localhost:9999}"
    )
    private String baseUrl;
    
    /**
     * 새로운 신청이 접수되었음을 관리자에게 알린다.
     */
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
    
    /**
     * 신청 승인 결과와 계정 활성화 링크를 신청자에게 알린다.
     */
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
        message.setSubject(
                "[WePlaNet] 등록 신청이 승인되었습니다 - 계정 활성화 안내"
        );
        message.setText(
                buildApprovalBody(
                        application,
                        username,
                        buildActivationUrl(verificationKey, rawToken),
                        expiresAt
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
    
    /**
     * 신청 반려 결과와 반려 사유를 신청자에게 알린다.
     */
    
    public void sendRejectionNotice(
            PartnershipApplication application
    ) {
        SimpleMailMessage message =
                new SimpleMailMessage();
        
        message.setTo(application.getEmail());
        message.setReplyTo(recipient);
        message.setSubject(
                "[WePlaNet] 등록 신청 검토 결과 안내"
        );
        message.setText(
                buildRejectionBody(application)
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
            LocalDateTime expiresAt
    ) {
        return """
                안녕하세요, %s님.

                WePlaNet %s 등록 신청이 승인되었습니다.

                ■ 신청 번호 : %d
                ■ 처리 결과 : 승인
                ■ 처리 시각 : %s

                아래 링크에서 비밀번호를 설정하시면
                소속사 페이지를 이용하실 수 있습니다.

                ■ 로그인 아이디 : %s
                ■ 활성화 링크   : %s
                ■ 링크 유효기간 : %s 까지

                보안을 위해 비밀번호는 관리자가 정하지 않습니다.
                링크는 한 번만 사용할 수 있으며, 기간이 지나면
                관리자에게 재발송을 요청해주세요.

                감사합니다.
                WePlaNet 드림
                """
                .formatted(
                        application.getContactName(),
                        application
                                .getApplicantType()
                                .getDisplayName(),
                        application.getId(),
                        formatTime(
                                application.getReviewedAt()
                        ),
                        username,
                        activationUrl,
                        formatTime(expiresAt)
                );
    }
    
    private String buildRejectionBody(
            PartnershipApplication application
    ) {
        return """
                안녕하세요, %s님.

                WePlaNet %s 등록 신청 검토 결과를 안내드립니다.

                ■ 신청 번호 : %d
                ■ 처리 결과 : 반려
                ■ 반려 사유 : %s
                ■ 처리 시각 : %s

                반려 사유를 확인하신 뒤 필요한 경우
                내용을 보완하여 다시 신청해주세요.

                감사합니다.
                WePlaNet 드림
                """
                .formatted(
                        application.getContactName(),
                        application
                                .getApplicantType()
                                .getDisplayName(),
                        application.getId(),
                        application.getRejectionReason(),
                        formatTime(
                                application.getReviewedAt()
                        )
                );
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