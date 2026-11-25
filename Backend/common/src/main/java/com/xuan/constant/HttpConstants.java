package com.xuan.constant;

/**
 * HTTP 请求头、响应头与请求体大小限制常量。
 */
public final class HttpConstants {

    public static final String HEADER_IF_REVISION = "If-Revision";
    public static final String HEADER_X_REVISION = "X-Revision";
    public static final String HEADER_RETRY_AFTER = "Retry-After";
    public static final String HEADER_CACHE_CONTROL = "Cache-Control";
    public static final String HEADER_REFERRER_POLICY = "Referrer-Policy";
    public static final String HEADER_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";
    public static final String HEADER_CONTENT_DISPOSITION = "Content-Disposition";
    public static final String HEADER_TRANSFER_ENCODING = "Transfer-Encoding";

    public static final String CACHE_NO_STORE = "no-store";
    public static final String REFERRER_NO_REFERRER = "no-referrer";
    public static final String NOSNIFF = "nosniff";
    public static final String APPLICATION_JSON_UTF8 = "application/json;charset=UTF-8";

    /**
     * 单个 JSON 请求体上限（8 MiB）。
     */
    public static final int MAX_JSON_BODY_BYTES = 8 * 1024 * 1024;
    /**
     * 单个文件上传请求体上限（61 MiB，音频上限 60 MiB 另留 1 MiB 表单开销）。
     */
    public static final long MAX_UPLOAD_REQUEST_BYTES = 61L * 1024 * 1024;

    private HttpConstants() {
    }
}
