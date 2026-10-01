package megane6.weplanet.service.email;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.i18n.PreferredLocaleResolver;
import org.springframework.context.MessageSource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Locale;

// SETTINGS-03 커밋5: 스케줄러에서 발송되므로 요청 로케일이 없다 - 받는 회원의 선호 언어로 메일을 만든다.
@Service
@RequiredArgsConstructor
public class DormantAccountNoticeService {

    private final JavaMailSender mailSender;
    private final MessageSource messageSource;

    public void sendDormantNotice(User user) {
        Locale locale = PreferredLocaleResolver.toLocale(user.getPreferredLanguage());
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(user.getEmail());
        message.setSubject(messageSource.getMessage("mail.dormant.notice.subject", null, locale));
        message.setText(messageSource.getMessage("mail.common.greeting", new Object[]{user.getNickname()}, locale) + "\n\n"
                + messageSource.getMessage("mail.dormant.notice.body", null, locale));
        mailSender.send(message);
    }

    public void sendDormantConvertedNotice(User user) {
        Locale locale = PreferredLocaleResolver.toLocale(user.getPreferredLanguage());
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(user.getEmail());
        message.setSubject(messageSource.getMessage("mail.dormant.converted.subject", null, locale));
        message.setText(messageSource.getMessage("mail.common.greeting", new Object[]{user.getNickname()}, locale) + "\n\n"
                + messageSource.getMessage("mail.dormant.converted.body", null, locale));
        mailSender.send(message);
    }
}
