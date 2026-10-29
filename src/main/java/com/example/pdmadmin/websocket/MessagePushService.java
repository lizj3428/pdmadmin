package com.example.pdmadmin.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 消息推送服务
 * 【作用】把"在线用户的 userId"和"他的 WebSocket 连接"一一对应起来。
 *        业务代码（比如推送任务触发时）只要调用 sendToUser(userId, 消息)，
 *        就能把消息实时发到该用户前端 —— 这就是论文里"实时推送通道"的发送端。
 */
@Component
public class MessagePushService {

    // userId -> 该用户的 WebSocket 连接（线程安全 Map）
    private static final Map<Long, WebSocketSession> SESSION_MAP = new ConcurrentHashMap<>();

    /** 用户连上 WS 时登记 */
    public void register(Long userId, WebSocketSession session) {
        SESSION_MAP.put(userId, session);
    }

    /** 用户断开 WS 时移除 */
    public void unregister(Long userId) {
        SESSION_MAP.remove(userId);
    }

    /** 判断某用户当前是否在线 */
    public boolean isOnline(Long userId) {
        WebSocketSession session = SESSION_MAP.get(userId);
        return session != null && session.isOpen();
    }

    /** 给指定用户推送一条消息（message 一般是 JSON 字符串） */
    public void sendToUser(Long userId, String message) {
        WebSocketSession session = SESSION_MAP.get(userId);
        if (session == null || !session.isOpen()) {
            return; // 用户不在线，直接丢弃（后续可扩展成"离线消息"）
        }
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(message));
            }
        } catch (IOException e) {
            // 发送失败（多半是连接已断），移除该连接
            SESSION_MAP.remove(userId);
        }
    }

    /** 给所有在线用户群发 */
    public void sendToAll(String message) {
        for (Long userId : SESSION_MAP.keySet()) {
            sendToUser(userId, message);
        }
    }
}
