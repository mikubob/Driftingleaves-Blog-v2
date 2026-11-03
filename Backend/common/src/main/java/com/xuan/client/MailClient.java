package com.xuan.client;

/**
 * 邮件发送接口，SMTP 接受邮件后才返回成功。
 */
public interface MailClient {

    /** 发送邮件，收件地址由业务层校验，标题和正文由服务端生成。 */
    void send(String to, String subject, String text);

    /** 发送带纯文本备选正文的 HTML 邮件，仅支持纯文本的实现仍发送备选正文。 */
    default void send(String to, String subject, String text, String html) {
        send(to, subject, text);
    }
}
