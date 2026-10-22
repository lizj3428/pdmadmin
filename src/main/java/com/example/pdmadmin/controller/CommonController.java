package com.example.pdmadmin.controller;

import com.example.pdmadmin.exception.BussinessException;
import com.example.pdmadmin.response.R;
import com.example.pdmadmin.response.ResponseCode;
import com.example.pdmadmin.util.Captcha;
import com.google.code.kaptcha.impl.DefaultKaptcha;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

@RestController
public class CommonController {
    /**
     * 生成验证码
     */
    @Resource
    private DefaultKaptcha defaultKaptcha;
    /*
    获取验证码
     */
    @RequestMapping("/common/getCaptcha")
    public R<Captcha> getCaptcha() {
        String captchaText = defaultKaptcha.createText();
        BufferedImage image = defaultKaptcha.createImage(captchaText);
        String base64Image = "";
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "jpg", os);
            base64Image = Base64.getEncoder().encodeToString(os.toByteArray());
        } catch (Exception e) {
            throw new BussinessException(ResponseCode.CREATE_CAPTCHA);
        }
        return null;
    }
}
