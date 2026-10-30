package com.example.pdmadmin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 推送任务表（论文表4-9） */
@Data
@TableName("push_task")
public class PushTask {
    @TableId(type = IdType.AUTO)
    private Long id;              // 任务ID
    private Long newsId;          // 关联资讯
    private String taskName;      // 任务名称
    private Integer targetType;   // 目标人群类型 1全部 2按年级 3按专业 4按标签
    private String targetValue;   // 目标人群取值，多个以逗号分隔
    private Integer channel;      // 推送渠道 1站内信 2微信 3企业微信
    private Integer pushType;     // 推送方式 1立即 2定时
    private LocalDateTime planTime; // 计划推送时间
    private Integer status;       // 任务状态 0待执行 1执行中 2已完成 3失败
    private Integer targetCount;  // 目标人数
    private Long createBy;        // 创建人
    private LocalDateTime createTime; // 创建时间
}
