package com.example.pdmadmin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 操作日志表（论文表4-14） */
@Data
@TableName("operation_log")
public class OperationLog {
    @TableId(type = IdType.AUTO)
    private Long id;            // 日志ID
    private Long userId;        // 操作人ID
    private Integer userType;   // 操作人类型 1管理员 2教师 3学生
    private String module;      // 操作模块
    private String operation;   // 操作描述
    private String requestUrl;  // 请求地址
    private String ipAddress;   // 操作IP
    private Integer costMs;     // 耗时毫秒
    private LocalDateTime createTime; // 操作时间
}
