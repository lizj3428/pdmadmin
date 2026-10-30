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
import com.example.pdmadmin.util.PasswordUtil;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
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
        //新增时密码必填，且入库前必须先加密：
        //直接 save 会把前端传来的明文原样写进数据库，等于白做加密
        String rawPwd = admin.getUserpwd();
        if (rawPwd == null || rawPwd.trim().isEmpty()) {
            throw new BusinessException(ResponseCode.PASSWORD_EMPTY);
        }
        admin.setUserpwd(PasswordUtil.encode(rawPwd));
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
        //密码密文也不返回给前端：密文泄露同样可被离线暴力破解，
        //顺便让「编辑」表单的密码框为空，避免把旧密文再提交回来被二次加密
        list.forEach(item -> item.setUserpwd(null));
        return R.data(pageInfo);
    }

    @Operation(summary = "修改管理员", description = "3.")
    @PostMapping("/admin/update")
    @SaCheckLogin
    public R update(@RequestBody Admin admin){
        String rawPwd = admin.getUserpwd();
        if (rawPwd == null || rawPwd.trim().isEmpty()) {
            //没传密码：置 null，MyBatis-Plus 默认不更新 null 字段，密码保持原值
            admin.setUserpwd(null);
        } else if (!PasswordUtil.isEncoded(rawPwd)) {
            //传了明文新密码：加密后再存
            admin.setUserpwd(PasswordUtil.encode(rawPwd));
        }
        //已经是 BCrypt 密文的（理论上不该出现）就原样保留，不做二次加密
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
     *
     * 流程：验证码 -> 查人 -> 验密码 -> 判状态 -> 明文自动升级 -> 记登录时间 -> 发 token
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
        //2.只按用户名查人。
        //  不能再把密码写进 SQL 条件：库里存的是 BCrypt 密文，每次加密结果都不同，
        //  用等号根本匹配不上，必须取出来再用 BCrypt 比对。
        LambdaQueryWrapper<Admin> adminWrapper = new LambdaQueryWrapper<>();
        adminWrapper.eq(Admin::getUsername, username);
        Admin admin = adminService.getOne(adminWrapper);

        //3.查不到人 和 密码不对，对外都用同一句提示，避免暴露「这个账号存不存在」
        if (admin == null || !PasswordUtil.matches(userpwd, admin.getUserpwd())) {
            throw new BusinessException(ResponseCode.USERNAME_USERPWD_ERROR);
        }

        //4.密码正确后，再看账号是否被禁用（论文表4-1 的 status 字段在这里真正生效）
        //  顺序不能反：先提示「已禁用」会让人借此探测出账号是存在的
        if (admin.getStatus() != null && admin.getStatus() == 0) {
            throw new BusinessException(ResponseCode.ACCOUNT_DISABLED);
        }

        //5.如果库里这串还是历史明文（老数据），趁这次登录成功顺手升级成 BCrypt，
        //  以后就不再有明文了。这一步只对老账号生效一次。
        if (!PasswordUtil.isEncoded(admin.getUserpwd())) {
            admin.setUserpwd(PasswordUtil.encode(userpwd));
        }
        //6.记录最后登录时间（表4-1 的 last_login_time 字段不再是摆设）
        admin.setLastLoginTime(LocalDateTime.now());
        adminService.updateById(admin);

        //7.根据ID进行登录
        StpUtil.login(admin.getId());
        //获取当前登录会话的token，赋值给admin的token属性
        admin.setToken(StpUtil.getTokenValue());
        //8.返回前清掉密码：登录响应里不需要密码，密文也不该出现在浏览器
        admin.setUserpwd(null);
        return R.data(admin);
    }

    @SaCheckLogin
    @PostMapping("/admin/logout")
    public R logout() {
        StpUtil.logout();
        return R.success();
    }



}
