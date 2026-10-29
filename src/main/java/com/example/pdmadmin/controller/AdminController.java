package com.example.pdmadmin.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.pdmadmin.entity.Admin;
import com.example.pdmadmin.exception.BusinessException;
import com.example.pdmadmin.response.R;
import com.example.pdmadmin.response.ResponseCode;
import com.example.pdmadmin.service.AdminService;
import com.example.pdmadmin.util.CaptchCache;
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
    @Resource
    private CaptchCache captchCache;
    @Operation(summary = "新增管理员", description = "1.")
    @PostMapping("/admin/add")
    @SaCheckLogin
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
    @SaCheckLogin
    public R<PageInfo<Admin>> list(@RequestBody Admin admin, @RequestParam Integer pageNum, @RequestParam Integer pageSize){
        LambdaQueryWrapper<Admin> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.like( admin.getName()!=null, Admin::getName, admin.getName());
        lambdaQueryWrapper.like( admin.getTel()!=null, Admin::getTel, admin.getTel());
        lambdaQueryWrapper.orderByDesc(Admin::getId);
        PageHelper.startPage(pageNum, pageSize);
        List<Admin> list = adminService.list(lambdaQueryWrapper);
        PageInfo<Admin> pageInfo = new PageInfo(list);
        return R.data(pageInfo);
    }

    @Operation(summary = "修改管理员", description = "3.")
    @PostMapping("/admin/update")
    @SaCheckLogin
    public R update(@RequestBody Admin admin){
        adminService.updateById(admin);
        return R.success();
    }
    @Operation(summary = "删除管理员", description = "4.")
    @PostMapping("/admin/del")
    @SaCheckLogin
    public R del(@RequestParam List<Long> ids){
        adminService.removeByIds(ids);
        return R.success();
    }


    /**
     * 管理员登录
     * @return
     */
    @Operation(summary = "管理员登录")
    @PostMapping("/admin/login")
    public R<Admin> login(@RequestParam String username, @RequestParam String userpwd,
                          @RequestParam String captchaId, @RequestParam String captchaCode){
        //1.校验验证码：输错或过期都不放行，校验通过后验证码立即失效
        if (!captchCache.validateCaptcha(captchaId, captchaCode)) {
            throw new BusinessException(ResponseCode.CAPTCHA_ERROR);
        }
        //2.根据传入的用户名和密码去数据库查询
        LambdaQueryWrapper<Admin> adminWrapper = new LambdaQueryWrapper<>();
        adminWrapper.eq(Admin::getUsername,username);
        adminWrapper.eq(Admin::getUserpwd,userpwd);
        Admin admin = adminService.getOne(adminWrapper);
        //数据库没有数据，就提示用户名密码错误
        if(admin == null){
            throw new BusinessException(ResponseCode.USERNAME_USERPWD_ERROR);
        }
        //根据ID进行登录
        StpUtil.login(admin.getId());
        //获取当前登录会话的token，赋值给admin的token属性
        admin.setToken(StpUtil.getTokenValue());
        return R.data(admin);
    }

    @SaCheckLogin
    @PostMapping("/admin/logout")
    public R logout() {
        StpUtil.logout();
        return R.success();
    }



}
