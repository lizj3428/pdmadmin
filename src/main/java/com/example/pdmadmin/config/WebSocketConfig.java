package com.example.pdmadmin.config;

import com.example.pdmadmin.websocket.AuthHandshakeInterceptor;
import com.example.pdmadmin.websocket.WebSocketServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置
 * 【作用】把 WebSocket 服务挂到地址 /pdmadmin/ws：
 *   - 前端连 ws://后端地址/pdmadmin/ws?pdmtoken=xxx 即可建立连接
 *   - 用 AuthHandshakeInterceptor 在握手时校验登录态（门禁）
 *   - setAllowedOrigins("*") 允许前端（不同端口）跨域连 WebSocket
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private WebSocketServer webSocketServer;
    @Autowired
    private AuthHandshakeInterceptor handshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(webSocketServer, "/ws")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOrigins("*");
    }
}
