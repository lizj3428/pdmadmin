package com.example.pdmadmin.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 消息推送服务
 * 【作用】把"在线用户"和"他的 WebSocket 连接"一一对应起来。
 *        业务代码（比如推送任务触发时）只要调用 sendToUser(loginKey, 消息)，
 *        就能把消息实时发到该用户前端 —— 这就是论文里"实时推送通道"的发送端。
 *
 * 【key 为什么是字符串】
 * 管理员、学生、教师是三张表，主键各自从 1 开始，纯数字会撞号。
 * 这里统一用 "admin:1" / "student:5" 这种带角色前缀的登录标识做 key，
 * 推送时才不会把消息发给另一个角色里同 id 的人（详见 LoginUtil）。
 */
@Component
public class MessagePushService {

    // loginKey -> 该用户的 WebSocket 连接（线程安全 Map）
    private static final Map<String, WebSocketSession> SESSION_MAP = new ConcurrentHashMap<>();

    /** 用户连上 WS 时登记 */
    public void register(String loginKey, WebSocketSession session) {
        SESSION_MAP.put(loginKey, session);
    }

    /** 用户断开 WS 时移除 */
    public void unregister(String loginKey) {
        SESSION_MAP.remove(loginKey);
    }

    /** 判断某用户当前是否在线 */
    public boolean isOnline(String loginKey) {
        WebSocketSession session = SESSION_MAP.get(loginKey);
        return session != null && session.isOpen();
    }

    /** 给指定用户推送一条消息（message 一般是 JSON 字符串） */
    public void sendToUser(String loginKey, String message) {
        WebSocketSession session = SESSION_MAP.get(loginKey);
        if (session == null || !session.isOpen()) {
            return; // 用户不在线，直接丢弃（后续可扩展成"离线消息"）
        }
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(message));
            }
        } catch (IOException e) {
            // 发送失败（多半是连接已断），移除该连接
            SESSION_MAP.remove(loginKey);
        }
    }

    /** 给所有在线用户群发 */
    public void sendToAll(String message) {
        for (String loginKey : SESSION_MAP.keySet()) {
            sendToUser(loginKey, message);
        }
    }
}
