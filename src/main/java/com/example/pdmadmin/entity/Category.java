package com.example.pdmadmin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 资讯分类表（论文表4-4） */
@Data
@TableName("category")
public class Category {
    @TableId(type = IdType.AUTO)
    private Long id;            // 分类ID
    private String name;        // 分类名称
    private Integer sort;       // 显示排序，值越小越靠前
    private String remark;      // 分类说明
    private Integer status;     // 启用状态 1启用 0停用
    private LocalDateTime createTime; // 创建时间
}
