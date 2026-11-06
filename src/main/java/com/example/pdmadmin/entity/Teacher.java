package com.example.pdmadmin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 教师表（论文表4-2） */
@Data
@TableName("teacher")
public class Teacher {
    @TableId(type = IdType.AUTO)
    private Long id;            // 教师ID
    private String teacherNo;   // 教工号
    private String password;    // 登录密码
    private String realName;    // 姓名
    private String gender;      // 性别
    private String phone;       // 联系电话
    private String deptName;    // 所属院系
    private String title;       // 职称
    private Integer adminFlag;   // 是否有管理端权限 1有 0无（普通教师只能投稿）
    private Integer status;     // 账号状态 1启用 0禁用
    private LocalDateTime createTime; // 创建时间

    /** token 由登录成功后动态生成（Sa-Token），数据库没有这一列，不参与读写 */
    @TableField(exist = false)
    private String token;
}
