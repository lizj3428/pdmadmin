package com.example.pdmadmin.util;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component

public class CaptchCache {
    // 缓存验证码
    private static ConcurrentHashMap<String, String> captchaMap = new ConcurrentHashMap<>();
    // 获取验证码
    public void storeCaptcha(String captchaId, String captcha) {
        captchaMap.put(captchaId, captcha);
    }
     //删除验证码
    public void removeCaptcha(String captchaId) {
        captchaMap.remove(captchaId);
    }

    public  boolean validateCaptcha(String captchaId, String captcha) {
        //获取存储的验证码
        String captchaCode = captchaMap.get(captchaId);
        // 验证码不存在
        if (captchaCode == null) {
            return false;
        }else {
            // 验证码匹配
            if (captchaCode.equals(captcha)){
                // 删除验证码
               captchaMap.remove(captchaId);
                return true;
            }else{
                //验证码错误
                return false;
            }


        }
    }

}
