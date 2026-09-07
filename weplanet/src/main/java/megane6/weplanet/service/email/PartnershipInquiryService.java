package megane6.weplanet.service.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

// [입점 신청] 메인 페이지 푸터의 "입점 신청" 폼에서 받은 내용을 운영자 메일로 보내는 서비스.
// 별도 테이블 없이 메일 발송만 한다(신청 내역을 DB에 남기는 건 아직 요구사항이 아님).
// 회원가입 인증메일과 같은 JavaMailSender(Gmail SMTP)를 그대로 쓴다.
@Slf4j
@Service
@RequiredArgsConstructor
public class PartnershipInquiryService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JavaMailSender mailSender;

    // 받는 사람은 운영자 계정 고정. 나중에 바뀔 수 있어서 properties로 덮어쓸 수 있게 해둠
    // (application.properties에 weplanet.partnership.recipient 를 적으면 그 값이 우선).
    @Value("${weplanet.partnership.recipient:admin4.wp@gmail.com}")
    private String recipient;

    // 보내는 사람(From)은 지정하지 않는다. Gmail SMTP는 인증한 계정 외의 주소로 보내는 걸 막기 때문에
    // spring.mail.username 계정이 그대로 발신자가 된다(회원가입 인증메일과 동일한 방식).
    // 여기서 @Value로 spring.mail.username 을 주입받으면 그 값이 다시 ${MAIL_USERNAME} 이라,
    // 환경변수가 없는 PC에서는 플레이스홀더 해석 실패로 서버가 아예 기동되지 않는다.
    // 신청자 주소는 아래 Reply-To에 넣어서, 운영자가 답장 버튼만 누르면 신청자에게 바로 회신되게 한다.

    public void send(PartnershipInquiry inquiry) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(recipient);
        message.setReplyTo(inquiry.email());
        message.setSubject("[WePlaNet 입점신청] " + inquiry.companyName());
        message.setText(buildBody(inquiry));

        mailSender.send(message);
        log.info("입점 신청 메일 발송: company={}, to={}", inquiry.companyName(), recipient);
    }

    private String buildBody(PartnershipInquiry inquiry) {
        return """
                WePlaNet 입점 신청이 접수되었습니다.
                
                ■ 업체명      : %s
                ■ 담당자      : %s
                ■ 회신 이메일 : %s
                ■ 연락처      : %s
                ■ 입점 분야   : %s
                ■ 접수 일시   : %s
                
                ■ 문의 내용
                %s
                
                ---
                이 메일은 WePlaNet 입점 신청 폼에서 자동 발송되었습니다.
                답장하시면 신청자(%s)에게 바로 회신됩니다.
                """
                .formatted(
                        inquiry.companyName(),
                        inquiry.managerName(),
                        inquiry.email(),
                        blankToDash(inquiry.phone()),
                        blankToDash(inquiry.category()),
                        LocalDateTime.now().format(TIME_FORMAT),
                        inquiry.message(),
                        inquiry.email());
    }

    private String blankToDash(String value) {
        return (value == null || value.isBlank()) ? "-" : value;
    }

    public record PartnershipInquiry(
            String companyName,
            String managerName,
            String email,
            String phone,
            String category,
            String message) {
    }
}
