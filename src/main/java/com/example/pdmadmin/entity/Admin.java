package com.example.pdmadmin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

//实体类
@Data
@TableName("admin")
@Schema(description = "管理员信息实体类")
public class Admin {
    @TableId(type = IdType.AUTO)
    private Long id;   /*主键ID */

    @Schema(description = "登录账号")
    private String username;

    /**
     * 登录密码。
     * 属性名沿用原来的 userpwd（前端登录页、登录接口都在用），
     * 数据库列名按论文表4-1 改为 password，用 @TableField 做映射，
     * 这样前端、Controller、Service 一行都不用改。
     */
    @TableField("password")
    private String userpwd;

    /** 姓名，数据库列名按论文为 real_name */
    @TableField("real_name")
    private String name;

    /** 联系电话，数据库列名按论文为 phone */
    @TableField("phone")
    private String tel;   /*电话*/

    /** 性别、头像论文表4-1 没有这两列，不参与数据库读写 */
    @TableField(exist = false)
    private String sex;
    @TableField(exist = false)
    private String headurl;    /* 头像*/

    @Schema(description = "角色类型 1超级管理员 2普通管理员")
    private Integer roleType;

    @Schema(description = "账号状态 1启用 0禁用")
    private Integer status;

    @Schema(description = "最后登录时间")
    private LocalDateTime lastLoginTime;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

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
