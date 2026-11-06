package com.xuan.security;

import com.xuan.exception.BusinessException;
import com.xuan.model.LoginSession;
import com.xuan.result.Result;
import com.xuan.session.SessionManager;
import com.xuan.vo.SessionVO;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;

/** 按接口所属端读取和签发登录 Cookie。 */
public enum WebSession {

    ADMIN("admin", "__Host-dl-admin-session", SessionManager.ADMIN_TIMEOUT_SECONDS),
    BLOG("blog", "__Host-dl-blog-session", SessionManager.BLOG_TIMEOUT_SECONDS);

    public static final String EXPECTED_USER = "X-Expected-User-Id";
    public static final String VERIFIED_ORIGIN = "com.xuan.security.WebSession.origin";

    private final String site;
    private final String cookieName;
    private final int timeoutSeconds;

    WebSession(String site, String cookieName, int timeoutSeconds) {
        this.site = site;
        this.cookieName = cookieName;
        this.timeoutSeconds = timeoutSeconds;
    }

    public static WebSession forPath(String path) {
        for (WebSession side : values()) {
            if (path.startsWith("/" + side.site + "/")) {
                return side;
            }
        }
        return null;
    }

    /** 核对页面账号，身份仍由当前登录会话决定。 */
    public static void requireExpectedUser(String expectedUserId, Object currentUserId) {
        if (!currentUserId.toString().equals(expectedUserId)) {
            throw new BusinessException(409, "SESSION_ACCOUNT_CHANGED", "登录账号已变化，请刷新页面后重试");
        }
    }

    public String site() {
        return site;
    }

    public String readToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        String token = null;
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                if (token != null || !SessionManager.isWebToken(cookie.getValue(), timeoutSeconds)) {
                    return null;
                }
                token = cookie.getValue();
            }
        }
        return token;
    }

    public ResponseEntity<Result<SessionVO>> login(LoginSession login) {
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, issue(login)).body(Result.ok(login.response()));
    }

    public String issue(LoginSession login) {
        return cookie(login.token(), timeoutSeconds);
    }

    public ResponseEntity<Result<Void>> logout(SessionManager sessions) {
        sessions.logout();
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie("", 0)).body(Result.ok(null));
    }

    private String cookie(String value, int maxAge) {
        return ResponseCookie.from(cookieName, value).secure(true).httpOnly(true)
                .sameSite("Strict").path("/").maxAge(maxAge).build().toString();
    }
}
