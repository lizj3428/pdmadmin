package com.example.pdmadmin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 标签表（论文表4-6） */
@Data
@TableName("tag")
public class Tag {
    @TableId(type = IdType.AUTO)
    private Long id;            // 标签ID
    private String name;        // 标签名称
    private Integer useCount;   // 引用次数
    private LocalDateTime createTime; // 创建时间
}
