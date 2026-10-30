package com.example.pdmadmin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 资讯表（论文表4-5） */
@Data
@TableName("news")
public class News {
    @TableId(type = IdType.AUTO)
    private Long id;              // 资讯ID
    private String title;         // 资讯标题
    private String summary;       // 资讯摘要
    private String content;       // 资讯正文
    private String coverUrl;      // 封面图片
    private Long categoryId;      // 所属分类
    private Long authorId;        // 创建人
    private Integer authorType;   // 创建人类型 1管理员 2教师
    private Integer level;        // 重要程度 1普通 2重要 3紧急
    private Integer auditStatus;  // 审核状态 0待审核 1通过 2驳回
    private String auditOpinion;  // 审核意见
    private Integer publishStatus; // 发布状态 0未发布 1已发布 2已下线
    private LocalDateTime publishTime; // 发布时间
    private Integer viewCount;    // 阅读量
    private LocalDateTime createTime; // 创建时间
    private LocalDateTime updateTime; // 更新时间
}
