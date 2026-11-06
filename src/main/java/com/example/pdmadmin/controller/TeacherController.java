package com.example.pdmadmin.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.pdmadmin.entity.Teacher;
import com.example.pdmadmin.exception.BusinessException;
import com.example.pdmadmin.mapper.TeacherMapper;
import com.example.pdmadmin.response.R;
import com.example.pdmadmin.response.ResponseCode;
import com.example.pdmadmin.util.LoginUtil;
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
 * 教师管理 + 教师登录
 *
 * 【两种教师】
 * admin_flag = 1：被授权进管理端的教师（例如负责宣传的老师），
 *                 登录后角色是 ["teacher","admin"]，管理端所有接口都能用。
 * admin_flag = 0：普通教师，角色只有 ["teacher"]，只能走 /tch/* 投稿线
 *                 （发资讯 -> 管理员审核 -> 通过后推送学生）。
 *
 * 【登录身份】loginId 存成 teacher:2 这种形式，和 admin:1 隔开。
 *            即使这个教师有管理权限，前缀也不会改成 admin，避免主键撞号。
 */
@Tag(name = "教师管理", description = "教师的增删改查")
@RestController
@SaCheckRole("admin")
public class TeacherController {

    @Resource
    private TeacherMapper teacherMapper;

    @Operation(summary = "教师分页列表", description = "支持教工号/姓名模糊搜索，按院系、状态筛选")
    @PostMapping("/teacher/list")
    @SaCheckLogin
    public R<PageInfo<Teacher>> list(@RequestBody Teacher teacher,
                                     @RequestParam Integer pageNum,
                                     @RequestParam Integer pageSize) {
        LambdaQueryWrapper<Teacher> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(teacher.getTeacherNo() != null && !teacher.getTeacherNo().trim().isEmpty(),
                Teacher::getTeacherNo, teacher.getTeacherNo());
        wrapper.like(teacher.getRealName() != null && !teacher.getRealName().trim().isEmpty(),
                Teacher::getRealName, teacher.getRealName());
        wrapper.eq(teacher.getDeptName() != null && !teacher.getDeptName().trim().isEmpty(),
                Teacher::getDeptName, teacher.getDeptName());
        wrapper.eq(teacher.getStatus() != null, Teacher::getStatus, teacher.getStatus());
        wrapper.orderByDesc(Teacher::getId);

        PageHelper.startPage(pageNum, pageSize);
        List<Teacher> list = teacherMapper.selectList(wrapper);
        PageInfo<Teacher> pageInfo = new PageInfo<>(list);
        list.forEach(item -> item.setPassword(null));
        return R.data(pageInfo);
    }

    @Operation(summary = "新增教师", description = "教工号唯一，密码入库前用 BCrypt 加密")
    @PostMapping("/teacher/add")
    @SaCheckLogin
    public R add(@RequestBody Teacher teacher) {
        String teacherNo = teacher.getTeacherNo();
        if (teacherNo == null || teacherNo.trim().isEmpty()) {
            throw new RuntimeException("教工号不能为空！");
        }
        // 教工号上有唯一索引 uk_teacher_no，先查一次给友好提示
        Long count = teacherMapper.selectCount(
                new LambdaQueryWrapper<Teacher>().eq(Teacher::getTeacherNo, teacherNo));
        if (count > 0) {
            throw new RuntimeException("教工号已存在！");
        }
        String rawPwd = teacher.getPassword();
        if (rawPwd == null || rawPwd.trim().isEmpty()) {
            throw new BusinessException(ResponseCode.PASSWORD_EMPTY);
        }
        teacher.setPassword(PasswordUtil.encode(rawPwd));
        if (teacher.getStatus() == null) {
            teacher.setStatus(1);
        }
        teacher.setCreateTime(LocalDateTime.now());
        teacherMapper.insert(teacher);
        return R.success();
    }

    @Operation(summary = "修改教师", description = "密码留空表示不修改；传明文才重新加密")
    @PostMapping("/teacher/update")
    @SaCheckLogin
    public R update(@RequestBody Teacher teacher) {
        if (teacher.getId() == null) {
            throw new RuntimeException("教师ID不能为空！");
        }
        // 教工号查重时排除自己
        String teacherNo = teacher.getTeacherNo();
        if (teacherNo != null && !teacherNo.trim().isEmpty()) {
            Long count = teacherMapper.selectCount(new LambdaQueryWrapper<Teacher>()
                    .eq(Teacher::getTeacherNo, teacherNo)
                    .ne(Teacher::getId, teacher.getId()));
            if (count > 0) {
                throw new RuntimeException("教工号已存在！");
            }
        }
        String rawPwd = teacher.getPassword();
        if (rawPwd == null || rawPwd.trim().isEmpty()) {
            teacher.setPassword(null);
        } else if (!PasswordUtil.isEncoded(rawPwd)) {
            teacher.setPassword(PasswordUtil.encode(rawPwd));
        }
        teacherMapper.updateById(teacher);
        return R.success();
    }

    @Operation(summary = "删除教师", description = "支持批量删除")
    @PostMapping("/teacher/del")
    @SaCheckLogin
    public R del(@RequestParam List<Long> ids) {
        teacherMapper.deleteByIds(ids);
        return R.success();
    }

    /* ==================== 以下三个接口供教师本人登录使用 ==================== */

    @Operation(summary = "教师登录",
            description = "返回的 adminFlag=1 表示该教师有管理端权限，前端据此决定登录后进哪个端")
    @PostMapping("/teacher/login")
    @SaIgnore   // 登录接口不可能已登录，必须跳过类上的 @SaCheckRole("admin")
    public R<Teacher> login(@RequestParam String teacherNo, @RequestParam String password) {
        Teacher teacher = teacherMapper.selectOne(
                new LambdaQueryWrapper<Teacher>().eq(Teacher::getTeacherNo, teacherNo));

        // 查不到人 和 密码不对 用同一句提示，避免暴露教工号是否存在
        if (teacher == null || !PasswordUtil.matches(password, teacher.getPassword())) {
            throw new BusinessException(ResponseCode.USERNAME_USERPWD_ERROR);
        }
        if (teacher.getStatus() != null && teacher.getStatus() == 0) {
            throw new BusinessException(ResponseCode.ACCOUNT_DISABLED);
        }
        // 历史明文密码趁登录顺手升级成 BCrypt
        if (!PasswordUtil.isEncoded(teacher.getPassword())) {
            teacher.setPassword(PasswordUtil.encode(password));
            teacherMapper.updateById(teacher);
        }

        // 前缀固定 teacher:，管理权限靠 StpInterfaceImpl 追加 admin 角色实现
        StpUtil.login(LoginUtil.loginId(LoginUtil.ROLE_TEACHER, teacher.getId()));
        teacher.setToken(StpUtil.getTokenValue());
        teacher.setPassword(null);
        return R.data(teacher);
    }

    @Operation(summary = "教师退出登录")
    @PostMapping("/teacher/logout")
    @SaIgnore   // 登出不能要求 admin 角色：普通教师本来就要能退出自己
    public R logout() {
        StpUtil.logout();
        return R.success();
    }

    @Operation(summary = "设置/取消教师的管理端权限", description = "adminFlag=1 授权进管理端，0 取消。取消后对方下一次请求立即失效")
    @PostMapping("/teacher/grantAdmin")
    @SaCheckLogin
    public R grantAdmin(@RequestParam Long id, @RequestParam Integer adminFlag) {
        if (id == null) {
            throw new RuntimeException("教师ID不能为空！");
        }
        Teacher teacher = teacherMapper.selectById(id);
        if (teacher == null) {
            throw new RuntimeException("教师不存在！");
        }
        Teacher update = new Teacher();
        update.setId(id);
        update.setAdminFlag(adminFlag != null && adminFlag == 1 ? 1 : 0);
        teacherMapper.updateById(update);
        return R.success();
    }
}
