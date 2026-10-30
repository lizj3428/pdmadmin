package com.example.pdmadmin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 学生表（论文表4-3） */
@Data
@TableName("student")
public class Student {
    @TableId(type = IdType.AUTO)
    private Long id;            // 学生ID
    private String studentNo;   // 学号
    private String password;    // 登录密码
    private String realName;    // 姓名
    private String gender;      // 性别
    private String grade;       // 年级
    private String major;       // 专业
    private String className;   // 班级
    private String phone;       // 联系电话
    private Integer pushChannel; // 消息接收渠道 1站内信 2微信 3两者
    private Integer disturbFlag; // 免打扰标志 1开启 0关闭
    private Integer status;      // 账号状态 1启用 0禁用
    private LocalDateTime createTime; // 创建时间
}
