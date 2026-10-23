package com.example.pdmadmin.controller;

import com.example.pdmadmin.exception.BussinessException;
import com.example.pdmadmin.response.R;
import com.example.pdmadmin.response.ResponseCode;
import com.example.pdmadmin.util.Captcha;
import com.example.pdmadmin.util.CaptchCache;
import com.google.code.kaptcha.impl.DefaultKaptcha;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.UUID;

/**
 * 公共接口控制器
 * 负责与登录状态无关的通用接口，例如获取登录页的图形验证码
 */
@RestController
public class CommonController {
    /**
     * 验证码生成器
     * 由 CaptchaConfig 中的 getDefaultKaptcha() 方法创建并注入，负责生成验证码文本和图片
     */
    @Resource
    private DefaultKaptcha defaultKaptcha;

    /**
     * 验证码缓存，用于存储和校验验证码
     */
    @Resource
    private CaptchCache captchaCache;

    /**
     * 获取图形验证码
     * 1. 使用 Kaptcha 生成随机验证码文本和对应图片
     * 2. 将图片写出为 jpg 字节流并转换为 Base64 编码，供前端直接展示
     * 3. 生成唯一的 captchaId 与图片一起返回，后续登录时由前端携带该 ID 校验验证码
     *
     * @return 包含验证码ID和图片（data:image/png;base64,xxx）的响应数据
     */
    @CrossOrigin
    @GetMapping ("/common/getCaptcha")
    public R<Captcha> getCaptcha() {
        String captchaText = defaultKaptcha.createText();
        BufferedImage image = defaultKaptcha.createImage(captchaText);
        String base64Code = "";
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "jpg", os);
            base64Code = Base64.getEncoder().encodeToString(os.toByteArray());
        } catch (Exception e) {
            throw new BussinessException(ResponseCode.CREATE_CAPTCHA);
        }
        Captcha captcha = new Captcha();
        captcha.setCaptchaImage("data:image/png;base64,"+base64Code);
        String captchaId = UUID.randomUUID().toString().replace("-", "");
        captcha.setCaptchaId(captchaId);
        captchaCache.storeCaptcha(captchaId, captchaText);

        return R.data(captcha);
    }
}
