package com.xuan.utils;

import cn.hutool.core.util.EscapeUtil;

import java.time.Year;

/** 生成邮件卡片，动态文本和链接先转义再放入 HTML。 */
public final class EmailTemplateUtils {

    private EmailTemplateUtils() {
    }

    /** 生成邮箱验证码卡片，标明操作用途和五分钟有效期。 */
    public static String verificationCode(String action, String code) {
        String content = """
                <h2 style="margin:0 0 24px;color:#333;font-size:20px;font-weight:500;">您好！</h2>
                <p style="margin:0 0 26px;">请使用以下验证码完成%s：</p>
                <div style="padding:24px 16px;background:#f9f9f9;border:1px solid #ddd;border-radius:8px;text-align:center;">
                    <p style="margin:0 0 16px;color:#666;font-size:14px;">验证码</p>
                    <div style="display:inline-block;padding:12px 24px;border:1px solid #333;border-radius:6px;background:#fff;">
                        <strong style="color:#333;font-size:28px;letter-spacing:6px;font-family:'Courier New',monospace;">%s</strong>
                    </div>
                    <p style="margin:16px 0 0;color:#888;font-size:13px;">有效期 5 分钟，请及时使用</p>
                </div>
                <p style="margin:24px 0 0;color:#888;font-size:13px;">请勿向他人透露验证码。若非本人操作，请忽略。后台验证码仅用于后台登录。</p>
                """.formatted(EscapeUtil.escapeHtml4(action), EscapeUtil.escapeHtml4(code));
        return layout("邮箱验证 · " + action, content);
    }

    /** 生成订阅确认邮件，链接仍进入需要登录的固定确认页。 */
    public static String subscriptionConfirmation(String url) {
        String content = """
                <h2 style="margin:0 0 24px;color:#333;font-size:20px;font-weight:500;">您好！</h2>
                <p style="margin:0 0 26px;">请登录发起申请的本站账号，并点击下方按钮确认邮件订阅：</p>
                <div style="padding:24px;background:#f9f9f9;border:1px solid #eee;border-radius:8px;">
                    %s
                    <p style="margin:20px 0 0;color:#888;font-size:13px;">链接 24 小时内有效。若未申请订阅，请忽略此邮件。</p>
                </div>
                """.formatted(button("确认订阅 →", url));
        return layout("确认邮件订阅", content);
    }

    /** 生成文章和留言通知卡片，正文由业务层控制，不加入私密原文。 */
    public static String notification(String title, String description, String detail, String buttonText,
            String url, String unsubscribeUrl) {
        String content = """
                <h2 style="margin:0 0 24px;color:#333;font-size:20px;font-weight:500;">您好！</h2>
                <p style="margin:0 0 26px;">%s</p>
                <div style="padding:24px;background:#f9f9f9;border:1px solid #eee;border-radius:8px;">
                    <h3 style="margin:0 0 24px;color:#333;font-size:20px;font-weight:500;overflow-wrap:anywhere;">%s</h3>
                    %s
                </div>
                <p style="margin:24px 0 0;color:#888;font-size:13px;">如不再接收通知，可
                    <a href="%s" style="color:#666;text-decoration:underline;">退订邮件</a>。
                </p>
                """.formatted(EscapeUtil.escapeHtml4(description), EscapeUtil.escapeHtml4(detail),
                button(buttonText, url), EscapeUtil.escapeHtml4(unsubscribeUrl));
        return layout(title, content);
    }

    /** 生成邮件中的操作按钮，链接使用双引号属性并转义。 */
    private static String button(String text, String url) {
        return """
                <a href="%s" style="display:inline-block;padding:12px 28px;background:#333;border-radius:6px;color:#fff;font-size:16px;text-decoration:none;">%s</a>
                """.formatted(EscapeUtil.escapeHtml4(url), EscapeUtil.escapeHtml4(text));
    }

    /** 生成共用页眉和页脚，使用内联样式，避免邮箱过滤 style 标签后丢失布局。 */
    private static String layout(String title, String content) {
        return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>%s</title>
                </head>
                <body style="margin:0;padding:0;background:#f5f5f5;font-family:'PingFang SC','Microsoft YaHei',Arial,sans-serif;">
                    <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#f5f5f5;">
                        <tr><td align="center" style="padding:40px 16px;">
                            <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:600px;background:#fff;border-radius:8px;overflow:hidden;box-shadow:0 2px 10px rgba(0,0,0,0.1);">
                                <tr><td align="center" style="padding:28px 20px;background:#333;">
                                    <h1 style="margin:0;color:#fff;font-size:24px;font-weight:500;">DriftingLeaves</h1>
                                    <p style="margin:12px 0 0;color:#bbb;font-size:14px;">—— %s ——</p>
                                </td></tr>
                                <tr><td style="padding:36px 30px;color:#555;font-size:15px;line-height:1.7;overflow-wrap:anywhere;">
                                    %s
                                </td></tr>
                                <tr><td align="center" style="padding:20px 16px;background:#222;color:#aaa;font-size:12px;line-height:1.8;">
                                    此为系统自动发送的邮件，请勿直接回复<br>
                                    &copy; %s DriftingLeaves - 保留所有权利
                                </td></tr>
                            </table>
                        </td></tr>
                    </table>
                </body>
                </html>
                """.formatted(EscapeUtil.escapeHtml4(title), EscapeUtil.escapeHtml4(title), content, Year.now());
    }
}
