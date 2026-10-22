package com.example.pdmadmin.util;

import lombok.Data;

//验证码实体类
@Data
public class Captcha {
        // 验证码ID
        private String captchaId;
        // 验证码图片
        private String captchaImage;

}
