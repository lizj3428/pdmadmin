package com.example.pdmadmin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 评论表（论文表4-13） */
@Data
@TableName("comment")
public class Comment {
    @TableId(type = IdType.AUTO)
    private Long id;             // 评论ID
    private Long newsId;         // 资讯ID
    private Long studentId;      // 评论学生
    private String content;      // 评论内容
    private Integer auditStatus; // 审核状态 0待审核 1通过 2驳回
    private LocalDateTime createTime; // 评论时间
}
