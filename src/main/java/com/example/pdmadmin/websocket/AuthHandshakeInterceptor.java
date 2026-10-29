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
 *   3. 有效就把 userId 放进连接属性里，后面 WebSocketServer 才知道"这是谁"
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
                    // Sa-Token：用 token 反查登录用户 ID；无效会抛异常
                    Object loginId = StpUtil.getLoginIdByToken(token);
                    if (loginId != null) {
                        attributes.put("userId", Long.valueOf(loginId.toString()));
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
