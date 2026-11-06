package com.example.pdmadmin.config;

import cn.dev33.satoken.stp.StpInterface;
import com.example.pdmadmin.entity.Student;
import com.example.pdmadmin.entity.Teacher;
import com.example.pdmadmin.mapper.StudentMapper;
import com.example.pdmadmin.mapper.TeacherMapper;
import com.example.pdmadmin.util.LoginUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Sa-Token 角色数据源
 *
 * 【作用】@SaCheckRole("admin") 校验时，Sa-Token 会回调这里问
 *        「某个 loginId 拥有哪些角色」。
 *
 * 【角色从哪来】
 * 基础角色仍然取 loginId 的前缀（admin:1 -> admin、teacher:2 -> teacher），
 * 这部分和项目原有的复合 loginId 约定一致，没有变化。
 *
 * 在此之上多做一步：教师、学生如果被授予了「管理端权限」（admin_flag = 1，
 * 例如学生会成员），额外追加一个 admin 角色，于是管理端那些
 * @SaCheckRole("admin") 的接口就对他放行了。
 *
 * 【为什么不直接把 loginId 改成 admin:2】
 * admin 表、teacher 表的 id 都从 1 自增，admin:2 会和「管理员表 id=2」撞号，
 * Sa-Token 会认为两者是同一个登录身份，WebSocket 连接也会互相顶掉。
 * 所以前缀永远表示「这个人属于哪张表」，管理权限只用角色来叠加。
 *
 * 【实时性】每次校验都查一次主键，管理员在后台取消授权后，对方下一次请求
 *        立刻失去权限，不需要等 token 过期。毕设场景数据量小，够用；
 *        以后并发高了再给这里加本地缓存。
 *
 * 【权限列表】本项目只做角色级控制，不做按钮级权限，getPermissionList 返回空集合。
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    @Resource
    private TeacherMapper teacherMapper;

    @Resource
    private StudentMapper studentMapper;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return Collections.emptyList();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        if (loginId == null) {
            return Collections.emptyList();
        }
        String s = String.valueOf(loginId);
        int idx = s.indexOf(':');
        // 没有前缀、或者只有前缀没主键，都说明不是本项目认可的登录身份
        if (idx <= 0 || idx == s.length() - 1) {
            return Collections.emptyList();
        }
        String role = s.substring(0, idx);
        Long id;
        try {
            id = Long.valueOf(s.substring(idx + 1));
        } catch (NumberFormatException e) {
            return Collections.emptyList();
        }

        List<String> roles = new ArrayList<>(2);
        roles.add(role);

        // 教师、学生：查一下有没有被授予管理端权限
        if (LoginUtil.ROLE_TEACHER.equals(role) || LoginUtil.ROLE_STUDENT.equals(role)) {
            Integer adminFlag = null;
            if (LoginUtil.ROLE_TEACHER.equals(role)) {
                Teacher teacher = teacherMapper.selectById(id);
                adminFlag = teacher == null ? null : teacher.getAdminFlag();
            } else {
                Student student = studentMapper.selectById(id);
                adminFlag = student == null ? null : student.getAdminFlag();
            }
            // 账号已被删除时 adminFlag 为 null，只保留基础角色
            if (adminFlag != null && adminFlag == 1) {
                roles.add(LoginUtil.ROLE_ADMIN);
            }
        }
        return roles;
    }
}
