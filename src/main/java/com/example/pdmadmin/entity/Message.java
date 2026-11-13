package com.example.pdmadmin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 站内消息表（论文表4-11） */
@Data
@TableName("message")
public class Message {
    @TableId(type = IdType.AUTO)
    private Long id;            // 消息ID
    // 接收人类型：1 学生 2 教师。教师通知复用 student_id 列存接收人ID，
    // 靠这一列区分，否则学生和教师的主键都是 1 会串信箱
    private Integer receiverType;
    private Long studentId;     // 接收人ID（receiver_type=1 时是学生，=2 时是教师）
    private String title;       // 消息标题
    private String content;     // 消息内容
    private Long newsId;        // 关联资讯
    private Integer msgType;    // 消息类型 1资讯通知 2系统公告 3审核结果通知
    private Integer readFlag;   // 已读标志 0未读 1已读
    private LocalDateTime createTime; // 创建时间
}
