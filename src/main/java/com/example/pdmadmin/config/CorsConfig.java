package com.example.pdmadmin.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 全局跨域配置（只管 HTTP 接口，管不到 WebSocket）
 * 【作用】前端（Vite 端口，如 5173）和后端（8080）是"不同源"，浏览器默认禁止前端直接访问后端接口。
 *        这里统一放行所有跨域请求，否则普通接口一调就被浏览器拦掉。
 *        改用全局配置后，Controller 方法上散落的 @CrossOrigin 就可以删掉，避免规则打架。
 *
 * 【注意】本类实现的是 WebMvcConfigurer，只对 Spring MVC 的 HTTP 接口生效，
 *        对 WebSocket 握手无效。WebSocket 的跨域由 WebSocketConfig 里的
 *        .setAllowedOrigins("*") 单独控制 —— WS 连不上时改这里没有任何作用。
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")                 // 对所有接口生效
                .allowedOriginPatterns("*")         // 允许所有来源（开发期方便；上线可改成前端正式域名）
                .allowedMethods("*")                // 允许所有请求方法 GET/POST/PUT/DELETE...
                .allowedHeaders("*")                // 允许所有请求头（含自定义 token 头 pdmtoken）
                .allowCredentials(true)             // 允许携带凭证（cookie / 授权信息）
                .exposedHeaders("pdmtoken")         // 把后端返回的 pdmtoken 暴露给前端读取
                .maxAge(3600);                      // 预检请求缓存 1 小时，减少重复校验
    }
}
