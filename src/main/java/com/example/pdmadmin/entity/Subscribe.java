package com.example.pdmadmin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 订阅关系表（论文表4-8） */
@Data
@TableName("subscribe")
public class Subscribe {
    @TableId(type = IdType.AUTO)
    private Long id;            // 记录ID
    private Long studentId;     // 学生ID
    private Integer targetType; // 订阅对象类型 1分类 2标签
    private Long targetId;      // 分类ID或标签ID
    private LocalDateTime createTime; // 订阅时间
}
