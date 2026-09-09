package megane6.weplanet.service.email;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.User;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DormantAccountNoticeService {

    private final JavaMailSender mailSender;

    public void sendDormantNotice(User user) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(user.getEmail());
        message.setSubject("[WePlaNet] 계정이 30일 후 휴면 상태로 전환됩니다");
        message.setText(user.getNickname() + "님, 안녕하세요.\n\n"
                + "장기간 로그인 기록이 없어 30일 후 휴면 상태로 전환될 예정입니다.\n"
                + "휴면 전환 후에도 이메일 인증만으로 언제든 다시 활성화할 수 있습니다.");
        mailSender.send(message);
    }

    public void sendDormantConvertedNotice(User user) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(user.getEmail());
        message.setSubject("[WePlaNet] 계정이 휴면 상태로 전환되었습니다");
        message.setText(user.getNickname() + "님, 안녕하세요.\n\n"
                + "장기간 미접속으로 휴면 처리되었습니다. 로그인 시 이메일 인증코드로 바로 해제할 수 있습니다.");
        mailSender.send(message);
    }
}
