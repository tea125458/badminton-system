package com.badminton.member;

import com.badminton.config.AuthHolder;
import com.badminton.config.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 驗證攔截器
 *
 * 從 Authorization: Bearer <token> 取出 JWT 並驗證，
 * 將解析出的使用者資訊存入：
 *   1. request attribute（供 Controller 直接取用）
 *   2. AuthHolder（靜態 ThreadLocal，供 Service 層取用）
 */
@Component
public class MemberAuthInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();

        // 1. OPTIONS 預檢請求直接放行（CORS 需要）
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 2. 嘗試從 Header 取出 JWT 並解析（不強制要求，供開放路徑讀取身份使用）
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7); // 去掉 "Bearer "
            try {
                Claims claims = jwtUtil.parseToken(token);
                setAuthAttributes(request, claims);
            } catch (io.jsonwebtoken.ExpiredJwtException e) {
                // 如果 Token 過期，仍嘗試從 e.getClaims() 獲取資訊，這對記錄「登出」日誌很有用
                setAuthAttributes(request, e.getClaims());
            } catch (Exception e) {
                // 其他 Token 無效情況
            }
        }

        // 3. 開放路徑（登入、註冊、登出不需要 Token，以及 GET 的公開查詢）
        if (uri.contains("/login") || uri.contains("/register") || uri.contains("/logout") || uri.contains("/reset-password") || uri.equals("/") ||
            (uri.contains("/api/pickup-games") && "GET".equalsIgnoreCase(request.getMethod())) ||
            (uri.contains("/api/pickup-game-signups") && "GET".equalsIgnoreCase(request.getMethod()))) {
            return true;
        }

        // 4. 需要驗證的 API：檢查是否有成功解析出使用者
        if (request.getAttribute("jwtUserId") == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "請先登入或 Token 已過期");
            return false;
        }

        // 5. 後台 API 權限檢查：/api/admins/**
        if (uri.contains("/api/admins")) {
            String role = (String) request.getAttribute("jwtRole");

            // 職員管理與操作日誌：僅限 MANAGER
            if (uri.contains("/api/admins/list") || uri.contains("/api/admins/add") || 
                uri.contains("/api/admins/logs") || uri.contains("/api/admins/search")) {
                if (!"MANAGER".equals(role)) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "管理者權限不足，僅限經理訪問");
                    return false;
                }
            }

            // 一般後台權限：需要 MANAGER 或 STAFF
            if (!"MANAGER".equals(role) && !"STAFF".equals(role)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "管理員權限不足");
                return false;
            }
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 請求結束後清除 ThreadLocal，避免 Thread 池重用導致資料洩漏
        AuthHolder.clear();
    }

    private void setAuthAttributes(HttpServletRequest request, Claims claims) {
        Integer userId = claims.get("userId", Integer.class);
        String username = claims.getSubject();
        String role = claims.get("role", String.class);

        request.setAttribute("jwtUserId", userId);
        request.setAttribute("jwtUsername", username);
        request.setAttribute("jwtRole", role);
        AuthHolder.set(userId, username, role);
    }
}
