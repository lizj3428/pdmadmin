package com.example.pdmadmin.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 验证码缓存
 * 【作用】把登录验证码存到 Redis（而不是程序内存）。
 *   - 好处：服务器重启不会丢、多台服务器也能共享同一份验证码
 *   - 存的是纯字符串，所以用 Spring 自动提供的 StringRedisTemplate 就够了
 *   - 验证码 2 分钟自动过期，防止被人拿旧码一直试
 * 注意：外部调用的方法名（storeCaptcha / removeCaptcha / validateCaptcha）保持不变，
 *       所以 AdminController、CommonController 里的登录逻辑一行都不用改。
 */
@Component
public class CaptchCache {

    // Redis 中 key 的前缀，避免和别的业务 key 撞名
    private static final String KEY_PREFIX = "captcha:";
    // 验证码有效期（分钟）
    private static final long EXPIRE_MINUTES = 2;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /** 存储验证码到 Redis，2 分钟后自动失效 */
    public void storeCaptcha(String captchaId, String captcha) {
        stringRedisTemplate.opsForValue().set(KEY_PREFIX + captchaId, captcha, EXPIRE_MINUTES, TimeUnit.MINUTES);
    }

    /** 删除验证码（例如登录成功后清除） */
    public void removeCaptcha(String captchaId) {
        stringRedisTemplate.delete(KEY_PREFIX + captchaId);
    }

    /** 校验验证码：正确则删除并返回 true，错误/过期返回 false */
    public boolean validateCaptcha(String captchaId, String captcha) {
        String captchaCode = stringRedisTemplate.opsForValue().get(KEY_PREFIX + captchaId);
        // 验证码不存在或已过期
        if (captchaCode == null) {
            return false;
        }
        // 比对用户输入的验证码
        if (captchaCode.equals(captcha)) {
            // 校验通过，立即删除，防止重复使用
            stringRedisTemplate.delete(KEY_PREFIX + captchaId);
            return true;
        }
        return false;
    }
}
