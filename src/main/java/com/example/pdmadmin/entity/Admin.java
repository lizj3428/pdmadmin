package com.example.pdmadmin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
//实体类
@Data
@TableName("admin")
@Schema(description = "管理员信息实体类")
public class Admin {
    @TableId(type = IdType.AUTO)
    public Long id;   /*主键ID */
    @Schema(description = "用户名")
    public String username;
    public String userpwd;

    public String name;
    public String sex;
    public String tel;   /*电话*/
    public String headurl;    /* 头像*/


}
