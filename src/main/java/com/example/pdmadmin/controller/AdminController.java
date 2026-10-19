package com.example.pdmadmin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.pdmadmin.entity.Admin;
import com.example.pdmadmin.response.R;
import com.example.pdmadmin.response.ResponseCode;
import com.example.pdmadmin.service.AdminService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;
/**
 * @Tag 这个注解：给当前控制器打上标签，在接口文档里作为分组名称
 * name：分组名字；description：分组描述
 */
@Tag(name = "管理员信息管理", description = "管理员相关接口：登录、查询信息、修改密码")
@RestController

public class AdminController {
    @Resource
    private AdminService adminService;
    @Operation(summary = "新增管理员", description = "1.")
    @PostMapping("/admin/add")
    @CrossOrigin
    public R add(@RequestBody Admin admin){
        LambdaQueryWrapper<Admin> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(Admin::getUsername, admin.getUsername());
        long count = adminService.count(lambdaQueryWrapper);
        if (count > 0) {
            throw new RuntimeException(ResponseCode.USERNAME_EXIST.getMessage());
        }
        adminService.save(admin);
        return R.success();
    }
    @Operation(summary = "管理员列表信息", description = "2.")
    @PostMapping("/admin/list")
    @CrossOrigin
    public R<PageInfo<Admin>> list(@RequestBody Admin admin, @RequestParam Integer pageNum, @RequestParam Integer pageSize){
        LambdaQueryWrapper<Admin> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.like( admin.getName()!=null, Admin::getName, admin.getName());
        lambdaQueryWrapper.like( admin.getTel()!=null, Admin::getTel, admin.getTel());
        PageHelper.startPage(pageNum, pageSize);
        List<Admin> list = adminService.list(lambdaQueryWrapper);
        PageInfo<Admin> pageInfo = new PageInfo(list);
        return R.data(pageInfo);
    }

    @Operation(summary = "修改管理员", description = "3.")
    @PostMapping("/admin/update")
    @CrossOrigin
    public R update(@RequestBody Admin admin){
        adminService.updateById(admin);
        return R.success();
    }
    @Operation(summary = "删除管理员", description = "4.")
    @PostMapping("/admin/del")
    @CrossOrigin
    public R<Object> del(@RequestParam Long id){
        adminService.removeById(id);
        return R.success();
    }
}
