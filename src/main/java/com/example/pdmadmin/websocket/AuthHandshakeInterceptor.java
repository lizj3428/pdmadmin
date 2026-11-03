package com.example.pdmadmin.websocket;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * 握手拦截器（连接前的登录校验）
 * 【作用】在 WebSocket 连接真正建立之前做一道"门禁"：
 *   1. 从地址栏参数 ?pdmtoken=xxx 取出 token
 *   2. 用 Sa-Token 校验这个 token 是否有效（是否真登录过）
 *   3. 有效就把登录标识（如 admin:1 / student:5）放进连接属性里，后面 WebSocketServer 才知道"这是谁"
 *   4. 无效或没带 token，直接拒绝握手
 */
@Component
public class AuthHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   org.springframework.web.socket.WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        // 只有 HTTP 升级请求才能取到 query 参数
        if (request instanceof ServletServerHttpRequest servletRequest) {
            String token = servletRequest.getServletRequest().getParameter("pdmtoken");
            if (token != null && !token.isEmpty()) {
                try {
                    // Sa-Token：用 token 反查登录标识；无效会抛异常。
                    // 登录标识形如 "admin:1" / "student:5"（见 LoginUtil），
                    // 直接原样作为连接的唯一键，靠前缀天然区分角色，不会串号
                    Object loginKey = StpUtil.getLoginIdByToken(token);
                    if (loginKey != null) {
                        attributes.put("loginKey", loginKey.toString());
                        return true; // 校验通过，放行
                    }
                } catch (Exception e) {
                    // token 无效，不放行
                    return false;
                }
            }
        }
        return false; // 没带 token 或校验失败，拒绝握手
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               org.springframework.web.socket.WebSocketHandler wsHandler,
                               Exception exception) {
        // 握手后无需额外处理
    }
}
