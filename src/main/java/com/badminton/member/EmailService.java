package com.badminton.member;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    /**
     * 寄送驗證碼到指定的 Email 信箱
     */
    public void sendVerificationCode(String toEmail, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("ygtq.badminton@gmail.com");
        message.setTo(toEmail);
        message.setSubject("【羽過天晴羽球館】密碼重設驗證碼");
        message.setText(
            "您好，\n\n" +
            "您的密碼重設驗證碼為：\n\n" +
            code + "\n\n" +
            "此驗證碼將在 5 分鐘後失效，請盡速完成密碼重設。\n" +
            "如果這不是您本人的操作，請忽略此信件。\n\n" +
            "— 羽過天晴羽球館"
        );
        mailSender.send(message);
    }
    
    /**
     * 寄送註冊驗證碼到指定的 Email 信箱
     */
    public void sendRegisterVerificationCode(String toEmail, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("ygtq.badminton@gmail.com");
        message.setTo(toEmail);
        message.setSubject("【羽過天晴羽球館】註冊驗證碼");
        message.setText(
            "您好，\n\n" +
            "您的註冊驗證碼為：\n\n" +
            code + "\n\n" +
            "此驗證碼將在 5 分鐘後失效，請盡速完成註冊。\n" +
            "如果這不是您本人的操作，請忽略此信件。\n\n" +
            "— 羽過天晴羽球館"
        );
        mailSender.send(message);
    }

    /**
     * 寄送註冊成功通知信
     */
    public void sendWelcomeEmail(String toEmail, String fullName) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("ygtq.badminton@gmail.com");
        message.setTo(toEmail);
        message.setSubject("【羽過天晴羽球館】註冊成功通知");
        message.setText(
            "您好 " + fullName + "，\n\n" +
            "歡迎加入羽過天晴羽球館！您的帳號已註冊成功。\n" +
            "現在您可以開始預約場地、參與活動以及使用官網服務囉。\n\n" +
            "祝您打球愉快！\n\n" +
            "— 羽過天晴羽球館"
        );
        mailSender.send(message);
    }
}
