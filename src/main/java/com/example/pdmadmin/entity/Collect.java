package com.example.pdmadmin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 收藏表（论文表4-12） */
@Data
@TableName("collect")
public class Collect {
    @TableId(type = IdType.AUTO)
    private Long id;            // 收藏ID
    private Long studentId;     // 学生ID
    private Long newsId;        // 资讯ID
    private LocalDateTime createTime; // 收藏时间
}
