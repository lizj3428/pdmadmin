package com.example.pdmadmin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 推送记录表（论文表4-10） */
@Data
@TableName("push_record")
public class PushRecord {
    @TableId(type = IdType.AUTO)
    private Long id;            // 记录ID
    private Long taskId;        // 任务ID
    private Long studentId;     // 接收学生
    private Integer channel;    // 推送渠道 1站内信 2微信 3企业微信
    private Integer sendStatus; // 发送状态 0待发送 1已提交 2已送达 3发送失败
    private String failReason;  // 失败原因
    private Integer retryCount; // 重试次数，上限3次
    private Integer costMs;     // 耗时毫秒
    private LocalDateTime sendTime; // 发送时间
}
