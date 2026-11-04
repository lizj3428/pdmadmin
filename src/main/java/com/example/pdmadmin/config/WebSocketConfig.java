package com.example.pdmadmin.config;

import com.example.pdmadmin.websocket.AuthHandshakeInterceptor;
import com.example.pdmadmin.websocket.WebSocketServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * WebSocket 配置
 * 【作用】注册 WebSocket 端点，给前端开一条长连接通道，用于实时推送。
 *
 * 【路径说明】下面注册的是 "/ws"，不含 context-path。
 *   Spring 会自动拼上 application.yml 里的 server.servlet.context-path=/pdmadmin，
 *   所以前端真正连接的地址是：ws://127.0.0.1:8080/pdmadmin/ws?pdmtoken=xxx
 *
 *   - AuthHandshakeInterceptor 在握手阶段校验 pdmtoken，未登录直接拒绝（相当于门禁）
 *   - setAllowedOrigins("*") 单独放行跨域：WebSocket 握手不走 CorsConfig，必须在这里配
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

    /**
     * 注册 JSR-356 容器（Tomcat 的 ServerContainer）
     *
     * 【为什么必须有这个 Bean】
     * 没有它，ServletContext 里就没有 ServerContainer 属性，
     * Spring 的 StandardWebSocketUpgradeStrategy 拿不到容器，
     * 握手会直接以 400 失败（日志里只有 "Upgrading to WebSocket" 然后就没了）。
     *
     * 顺带把消息缓冲区调到 8KB：推送的资讯摘要可能超过默认的 4KB。
     */
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(8192);
        container.setMaxBinaryMessageBufferSize(8192);
        return container;
    }
}
