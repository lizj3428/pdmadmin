package com.example.pdmadmin.websocket;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * WebSocket 连接处理器
 * 【作用】真正管理每个在线用户的连接：
 *   - 连上时：从握手时存好的 loginKey（如 admin:1）取出，登记到 MessagePushService
 *   - 断开时：从 MessagePushService 移除，避免推送给已掉线的用户
 *   - 收到消息：目前用于心跳（前端定时发 "ping"，后端回 "pong"，保持连接活跃）
 */
@Component
public class WebSocketServer extends TextWebSocketHandler {

    @Autowired
    private MessagePushService messagePushService;

    /** 连接建立后触发 */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // loginKey 是握手拦截器从 pdmtoken 解析出来的登录标识，形如 admin:1 / student:5
        String loginKey = (String) session.getAttributes().get("loginKey");
        if (loginKey != null) {
            messagePushService.register(loginKey, session);
        }
    }

    /** 连接关闭后触发 */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String loginKey = (String) session.getAttributes().get("loginKey");
        if (loginKey != null) {
            messagePushService.unregister(loginKey);
        }
    }

    /** 收到前端发来的文本消息 */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        // 简单心跳：前端发 "ping"，后端回 "pong"
        if ("ping".equals(payload)) {
            session.sendMessage(new TextMessage("pong"));
        }
    }
}
