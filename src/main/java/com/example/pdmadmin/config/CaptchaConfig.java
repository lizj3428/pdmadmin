package com.example.pdmadmin.config;

import com.google.code.kaptcha.impl.DefaultKaptcha;
import com.google.code.kaptcha.util.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Properties;

/**
 * 验证码配置类
 * 手工创建 Kaptcha 的 DefaultKaptcha 实例并注册为 Bean，通过 Properties 定制验证码的样式与内容
 */
@Configuration
public class CaptchaConfig {
    /**
     * 创建验证码生成器 Bean
     *
     * @return 已应用验证码配置的 DefaultKaptcha 实例
     */
    @Bean
    public DefaultKaptcha getDefaultKaptcha() {
        DefaultKaptcha defaultKaptcha = new DefaultKaptcha();
        Properties properties = new Properties();
        // 不绘制边框
        properties.setProperty("kaptcha.border", "no");
        properties.setProperty("kaptcha.border.color", "34,114,200");
        // 验证码图片宽高
        properties.setProperty("kaptcha.image.width", "125");
        properties.setProperty("kaptcha.image.height", "40");
        // 验证码字符范围与长度（只使用数字）
        properties.setProperty("kaptcha.textproducer.char.string", "123456789");
        properties.setProperty("kaptcha.textproducer.char.length", "4");
        // 验证码字体与字号
        properties.setProperty("kaptcha.textproducer.font.names", "Arial,Arial Narrow,Serif,Helvetica,Tahoma,Times New Roman,Verdana");
        properties.setProperty("kaptcha.textproducer.font.size", "38");

        // 背景渐变色：起始色与结束色都设为白色，即纯白背景
        properties.setProperty("kaptcha.background.clear.from", "white");
        properties.setProperty("kaptcha.background.clear.to", "white");

        Config config = new Config(properties);
        defaultKaptcha.setConfig(config);
        return defaultKaptcha;
    }
}
