package com.example.pdmadmin.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum ResponseCode {
    SUCCESS(200,"操作成功！"),
    ERROR(500,"操作失败！"),
    CREATE_CAPTCHA(2001,"验证码失败！"),
    USERNAME_EXIST(1001,"用户名已存在！");
    /*
    响应状态码
     */
    private Integer code;
    /*
    响应提示信息
     */
    private String message;

}
