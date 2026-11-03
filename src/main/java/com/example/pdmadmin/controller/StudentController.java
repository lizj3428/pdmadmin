package com.example.pdmadmin.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.pdmadmin.entity.Student;
import com.example.pdmadmin.exception.BusinessException;
import com.example.pdmadmin.mapper.StudentMapper;
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
 * 学生管理
 * 【作用】学生是推送的真正接收方：推送任务算出来的「目标人群」最终都落在学生身上。
 *        所以学生必须能登录、能被按年级/专业筛选，推送模块才有数据可用。
 *
 * 【登录身份】loginId 存成 student:5 这种形式，和 admin:1 隔开，
 *            否则管理员 id=1 和学生 id=1 会互相顶掉 WebSocket 连接（详见 LoginUtil）。
 */
@Tag(name = "学生管理", description = "学生的增删改查与学生端登录")
@RestController
public class StudentController {

    @Resource
    private StudentMapper studentMapper;

    @Operation(summary = "学生分页列表", description = "支持学号/姓名模糊搜索，按年级、专业、状态筛选")
    @PostMapping("/student/list")
    @SaCheckLogin
    public R<PageInfo<Student>> list(@RequestBody Student student,
                                     @RequestParam Integer pageNum,
                                     @RequestParam Integer pageSize) {
        LambdaQueryWrapper<Student> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(student.getStudentNo() != null && !student.getStudentNo().trim().isEmpty(),
                Student::getStudentNo, student.getStudentNo());
        wrapper.like(student.getRealName() != null && !student.getRealName().trim().isEmpty(),
                Student::getRealName, student.getRealName());
        // 年级、专业用等值匹配：推送定向就是按这两个字段整批选人的
        wrapper.eq(student.getGrade() != null && !student.getGrade().trim().isEmpty(),
                Student::getGrade, student.getGrade());
        wrapper.eq(student.getMajor() != null && !student.getMajor().trim().isEmpty(),
                Student::getMajor, student.getMajor());
        wrapper.eq(student.getStatus() != null, Student::getStatus, student.getStatus());
        wrapper.orderByDesc(Student::getId);

        PageHelper.startPage(pageNum, pageSize);
        List<Student> list = studentMapper.selectList(wrapper);
        PageInfo<Student> pageInfo = new PageInfo<>(list);
        // 密码密文同样不回传：密文泄露也能被离线暴力破解
        list.forEach(item -> item.setPassword(null));
        return R.data(pageInfo);
    }

    @Operation(summary = "新增学生", description = "学号唯一，密码入库前用 BCrypt 加密")
    @PostMapping("/student/add")
    @SaCheckLogin
    public R add(@RequestBody Student student) {
        String studentNo = student.getStudentNo();
        if (studentNo == null || studentNo.trim().isEmpty()) {
            throw new RuntimeException("学号不能为空！");
        }
        // 学号上有唯一索引 uk_student_no，这里先查一次是为了给友好提示
        Long count = studentMapper.selectCount(
                new LambdaQueryWrapper<Student>().eq(Student::getStudentNo, studentNo));
        if (count > 0) {
            throw new RuntimeException("学号已存在！");
        }
        String rawPwd = student.getPassword();
        if (rawPwd == null || rawPwd.trim().isEmpty()) {
            throw new BusinessException(ResponseCode.PASSWORD_EMPTY);
        }
        student.setPassword(PasswordUtil.encode(rawPwd));
        // 下面三个字段数据库有 NOT NULL 默认值，但实体里是 null 时
        // MyBatis-Plus 会把它们排除在 INSERT 之外，默认值照常生效；
        // 这里显式给值只是为了让前端不传时状态也明确
        if (student.getPushChannel() == null) {
            student.setPushChannel(1);
        }
        if (student.getDisturbFlag() == null) {
            student.setDisturbFlag(0);
        }
        if (student.getStatus() == null) {
            student.setStatus(1);
        }
        student.setCreateTime(LocalDateTime.now());
        studentMapper.insert(student);
        return R.success();
    }

    @Operation(summary = "修改学生", description = "密码留空表示不修改；传明文才重新加密")
    @PostMapping("/student/update")
    @SaCheckLogin
    public R update(@RequestBody Student student) {
        if (student.getId() == null) {
            throw new RuntimeException("学生ID不能为空！");
        }
        // 学号唯一性要排除自己，否则改别的字段也会撞自己的学号
        String studentNo = student.getStudentNo();
        if (studentNo != null && !studentNo.trim().isEmpty()) {
            Long count = studentMapper.selectCount(new LambdaQueryWrapper<Student>()
                    .eq(Student::getStudentNo, studentNo)
                    .ne(Student::getId, student.getId()));
            if (count > 0) {
                throw new RuntimeException("学号已存在！");
            }
        }
        String rawPwd = student.getPassword();
        if (rawPwd == null || rawPwd.trim().isEmpty()) {
            // 没传密码：置 null，MyBatis-Plus 不更新 null 字段，原密码保留
            student.setPassword(null);
        } else if (!PasswordUtil.isEncoded(rawPwd)) {
            student.setPassword(PasswordUtil.encode(rawPwd));
        }
        studentMapper.updateById(student);
        return R.success();
    }

    @Operation(summary = "删除学生", description = "支持批量删除")
    @PostMapping("/student/del")
    @SaCheckLogin
    public R del(@RequestParam List<Long> ids) {
        studentMapper.deleteByIds(ids);
        return R.success();
    }

    /**
     * 学生登录
     *
     * 流程：查人 -> 验密码 -> 判状态 -> 发 token
     * 学生端不加验证码：移动端每次登录都要认图太折腾，安全性由 BCrypt + token 有效期保证。
     */
    @Operation(summary = "学生登录", description = "学号 + 密码登录，成功后返回 token")
    @PostMapping("/student/login")
    public R<Student> login(@RequestParam String studentNo, @RequestParam String password) {
        Student student = studentMapper.selectOne(
                new LambdaQueryWrapper<Student>().eq(Student::getStudentNo, studentNo));

        // 查不到人 和 密码不对 用同一句提示，避免暴露学号是否存在
        if (student == null || !PasswordUtil.matches(password, student.getPassword())) {
            throw new BusinessException(ResponseCode.USERNAME_USERPWD_ERROR);
        }
        // 禁用账号不给登录
        if (student.getStatus() != null && student.getStatus() == 0) {
            throw new BusinessException(ResponseCode.ACCOUNT_DISABLED);
        }
        // 历史明文密码趁登录顺手升级成 BCrypt
        if (!PasswordUtil.isEncoded(student.getPassword())) {
            student.setPassword(PasswordUtil.encode(password));
            studentMapper.updateById(student);
        }

        // loginId 带 student: 前缀，和管理员、教师彻底分开
        StpUtil.login(LoginUtil.loginId(LoginUtil.ROLE_STUDENT, student.getId()));
        student.setToken(StpUtil.getTokenValue());
        // 返回前清密码
        student.setPassword(null);
        return R.data(student);
    }

    @Operation(summary = "学生退出登录")
    @PostMapping("/student/logout")
    @SaCheckLogin
    public R logout() {
        StpUtil.logout();
        return R.success();
    }
}
