package com.badminton.pickupgame;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 🌟 臨打揪團專用的 Email 服務
 * 獨立於 member 套件的 EmailService，避免改到其他組員的程式碼
 */
@Service
public class PickupGameEmailService {

    @Autowired
    private JavaMailSender mailSender;

    /**
     * 團主群發公告：逐一寄送 Email 給已報名的球友
     * 使用 for-each 逐封寄送，單封失敗不影響其他人
     *
     * @param recipients 收件人 Email 清單
     * @param hostName   團主姓名（用於信件署名）
     * @param gameInfo   球局摘要（日期+時段+場地）
     * @param content    團主輸入的公告內容
     * @return 成功寄送的封數
     */
    public int sendBroadcast(List<String> recipients, String hostName,
                             String gameInfo, String content) {
        int successCount = 0;
        for (String toEmail : recipients) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom("ygtq.badminton@gmail.com");
                message.setTo(toEmail);
                message.setSubject("【羽過天晴】您報名的球局有最新公告");
                message.setText(
                    "Hi，球友您好！\n\n" +
                    "您報名的球局「" + gameInfo + "」，團主有新的公告：\n\n" +
                    "──────────────\n" +
                    content + "\n" +
                    "──────────────\n\n" +
                    "發佈者：" + hostName + "\n" +
                    "如有任何問題，請直接聯繫團主。\n\n" +
                    "— 羽過天晴羽球館"
                );
                mailSender.send(message);
                successCount++;
            } catch (Exception e) {
                // 單封寄失敗不中斷迴圈，記錄錯誤後繼續
                System.err.println("寄送公告信失敗 → " + toEmail + "：" + e.getMessage());
            }
        }
        return successCount;
    }

    /**
     * 🌟 移除通知：當團主踢除球友時，自動寄送通知信
     *
     * @param toEmail    被移除球友的 Email
     * @param memberName 被移除球友的姓名
     * @param gameInfo   球局摘要（日期+時段）
     * @param hostName   團主姓名
     */
    @Async
    public void sendRemovalNotice(String toEmail, String memberName,
                                  String gameInfo, String hostName, String hostPhone) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("ygtq.badminton@gmail.com");
            message.setTo(toEmail);
            message.setSubject("【羽過天晴】揪團參與狀態變更通知");
            String contactInfo = hostName;
            if (hostPhone != null && !hostPhone.trim().isEmpty()) {
                contactInfo += "，電話：" + hostPhone;
            }
            message.setText(
                memberName + " 您好，\n\n" +
                "很抱歉通知您，您報名的球局「" + gameInfo + "」" +
                "由於名額調整或團主安排，已取消您的報名。\n\n" +
                "如有預付費用，請聯繫團主（" + contactInfo + "）處理。\n" +
                "造成不便敬請見諒。\n\n" +
                "如有任何疑問，歡迎透過平台聯繫團主。\n\n" +
                "— 羽過天晴羽球館"
            );
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("寄送移除通知失敗 → " + toEmail + "：" + e.getMessage());
        }
    }

    /**
     * 🌟 取消揪團通知：當團主取消揪團時，自動寄送通知信給所有報名者
     *
     * @param recipients 收件人 Email 清單
     * @param hostName   團主姓名
     * @param gameInfo   球局摘要（日期+時段）
     */
    @Async
    public void sendCancellationNotice(List<String> recipients, String hostName, String hostPhone, String gameInfo) {
        for (String toEmail : recipients) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom("ygtq.badminton@gmail.com");
                message.setTo(toEmail);
                message.setSubject("【羽過天晴】揪團取消通知");
                String contactInfo = hostName;
                if (hostPhone != null && !hostPhone.trim().isEmpty()) {
                    contactInfo += "，電話：" + hostPhone;
                }
                message.setText(
                    "Hi，球友您好！\n\n" +
                    "很抱歉通知您，您報名的球局「" + gameInfo + "」" +
                    "由於團主安排，目前已經取消。\n\n" +
                    "造成不便敬請見諒。\n" +
                    "如有預付費用，請聯繫團主（" + contactInfo + "）處理退費事宜。\n\n" +
                    "— 羽過天晴羽球館"
                );
                mailSender.send(message);
            } catch (Exception e) {
                System.err.println("寄送取消通知失敗 → " + toEmail + "：" + e.getMessage());
        }
    }

    /**
     * 🌟 聯絡主揪：由球友透過系統發送信件給主揪
     *
     * @param hostEmail   主揪的 Email
     * @param hostName    主揪姓名
     * @param memberName  發信球友姓名
     * @param gameInfo    球局摘要
     * @param content     發信內容
     */
    @Async
    public void sendContactHostEmail(String hostEmail, String hostName, String memberName, String gameInfo, String content) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("ygtq.badminton@gmail.com");
            message.setTo(hostEmail);
            message.setSubject("【羽過天晴】有球友向您詢問揪團資訊");
            message.setText(
                hostName + " 您好，\n\n" +
                "球友「" + memberName + "」透過系統針對您的球局「" + gameInfo + "」向您發送了訊息：\n\n" +
                "──────────────\n" +
                content + "\n" +
                "──────────────\n\n" +
                "若要回覆，請登入羽過天晴系統，至「管理揪團」使用『寄出公告信』功能通知所有球友。\n" +
                "為保護球友個資，系統不提供個人聯絡資訊。\n\n" +
                "— 羽過天晴羽球館"
            );
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("寄送聯絡主揪信件失敗 → " + hostEmail + "：" + e.getMessage());
        }
    }
}
