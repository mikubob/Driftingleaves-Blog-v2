package com.xuan.client;

/**
 * 发送邮件；只有 SMTP 接受后才返回，失败不能伪装成功。
 */
public interface MailClient {

    /** 收件地址由订阅业务确认；正文和标题只由服务端生成。 */
    void send(String to, String subject, String text);
}
