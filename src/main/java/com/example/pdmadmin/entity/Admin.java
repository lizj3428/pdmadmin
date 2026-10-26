package com.example.pdmadmin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
//实体类
@Data
@TableName("admin")
@Schema(description = "管理员信息实体类")
public class Admin {
    @TableId(type = IdType.AUTO)
    private Long id;   /*主键ID */
    @Schema(description = "用户名")
     String username;
    private String userpwd;

    private String name;
    private String sex;
    private String tel;   /*电话*/
    private String headurl;    /* 头像*/
    /**
     * 验证码相关字段只用于接收前端参数，数据库表中没有对应列，
     * 必须标注 @TableField(exist = false)，否则 MyBatis-Plus 会把
     * captcha_id、captcha_code 当作真实列生成 SQL，导致 Unknown column 报错
     */
    @TableField(exist = false)
    private String captchaId;
    @TableField(exist = false)
    private String captchaCode;
    /**
     * token 由登录成功后动态生成（Sa-Token），不存数据库，
     * 同样必须标注 @TableField(exist = false) 防止生成 SQL 时带上 token 列
     */
    @TableField(exist = false)
    private String token;




}
