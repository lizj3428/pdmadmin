package com.example.pdmadmin.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.example.pdmadmin.entity.Teacher;
import com.example.pdmadmin.mapper.TeacherMapper;
import com.example.pdmadmin.response.R;
import com.example.pdmadmin.util.LoginUtil;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 教师个人资料
 *
 * 【范围】普通教师只能看和改自己那条记录，id 一律从登录态里取，不接受前端传。
 *        工号、院系、职称、账号状态属于管理员维护的内容，这里不开放修改。
 *
 * 【角色】类上锁 @SaCheckRole("teacher")，学生调 403；
 *        被授权进管理端的教师角色是 [teacher, admin]，同样能用。
 */
@io.swagger.v3.oas.annotations.tags.Tag(name = "教师个人资料", description = "教师查看与维护本人资料")
@RestController
@SaCheckRole("teacher")
public class TchProfileController {

    @Resource
    private TeacherMapper teacherMapper;

    @Operation(summary = "查看本人资料")
    @PostMapping("/tch/profile")
    @SaCheckLogin
    public R<Teacher> profile() {
        Teacher teacher = teacherMapper.selectById(LoginUtil.currentId());
        if (teacher == null) {
            throw new RuntimeException("教师信息不存在！");
        }
        // 密码不返回给前端，哪怕是密文
        teacher.setPassword(null);
        return R.data(teacher);
    }

    @Operation(summary = "修改本人资料", description = "只允许修改姓名与联系电话")
    @PostMapping("/tch/profile/update")
    @SaCheckLogin
    public R update(@RequestParam(required = false) String realName,
                    @RequestParam(required = false) String phone) {
        Teacher patch = new Teacher();
        patch.setId(LoginUtil.currentId());

        if (realName != null) {
            String name = realName.trim();
            if (name.isEmpty()) {
                throw new RuntimeException("姓名不能为空！");
            }
            if (name.length() > 20) {
                throw new RuntimeException("姓名不能超过 20 个字符！");
            }
            patch.setRealName(name);
        }

        if (phone != null) {
            String tel = phone.trim();
            if (tel.isEmpty()) {
                throw new RuntimeException("联系电话不能为空！");
            }
            if (!tel.matches("\\d{11}")) {
                throw new RuntimeException("联系电话必须是 11 位数字！");
            }
            patch.setPhone(tel);
        }

        // updateById 默认忽略 null 字段，只会更新上面显式赋过值的那几列
        teacherMapper.updateById(patch);
        return R.success("资料已保存");
    }
}
