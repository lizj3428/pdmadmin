package com.example.pdmadmin.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.pdmadmin.entity.Teacher;
import com.example.pdmadmin.exception.BusinessException;
import com.example.pdmadmin.mapper.TeacherMapper;
import com.example.pdmadmin.response.R;
import com.example.pdmadmin.response.ResponseCode;
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
 * 教师管理
 * 【作用】教师负责投稿，论文里 author_type=2 就是教师发的资讯。
 *        本轮只做增删改查；教师登录接口等「教师投稿」那一轮一起做，
 *        因为投稿还要按 authorType 区分权限，单独做登录没意义。
 */
@Tag(name = "教师管理", description = "教师的增删改查")
@RestController
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
}
