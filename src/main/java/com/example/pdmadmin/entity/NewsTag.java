package com.example.pdmadmin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 资讯标签关联表（论文表4-7） */
@Data
@TableName("news_tag")
public class NewsTag {
    @TableId(type = IdType.AUTO)
    private Long id;            // 记录ID
    private Long newsId;        // 资讯ID
    private Long tagId;         // 标签ID
    private LocalDateTime createTime; // 创建时间
}
