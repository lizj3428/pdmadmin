package com.example.pdmadmin.util;

import cn.dev33.satoken.stp.StpUtil;

/**
 * 登录身份工具类
 *
 * 【为什么需要它】
 * 管理员、学生、教师是三张独立的表，主键各自从 1 开始自增。
 * 如果用纯数字做 Sa-Token 的 loginId，admin 的 id=1 和 student 的 id=1
 * 会被认成同一个登录身份，WebSocket 的「userId -> 连接」表也会串号：
 * 学生连上来会顶掉管理员那条连接。
 *
 * 【做法】
 * 登录时统一把 loginId 存成「角色:主键」的形式，例如 admin:1、student:1。
 * 角色前缀不一样，两套身份就永远不会相等。
 *
 * 【使用约定】
 * 全项目禁止再直接调用 StpUtil.getLoginIdAsLong()：
 * 它遇到 "admin:1" 这种字符串会抛 NumberFormatException。
 * 需要当前用户 id 一律走 LoginUtil.currentId()。
 */
public class LoginUtil {

    /** 角色前缀：必须和数据库表名一致，方便排查问题时一眼看懂 */
    public static final String ROLE_ADMIN = "admin";
    public static final String ROLE_STUDENT = "student";
    public static final String ROLE_TEACHER = "teacher";

    private LoginUtil() {
    }

    /** 拼出登录用的 loginId，例如 loginId("admin", 1L) -> "admin:1" */
    public static String loginId(String role, Long id) {
        return role + ":" + id;
    }

    /** 当前登录用户的完整 loginId（形如 admin:1）；未登录返回 null */
    public static String currentLoginId() {
        if (!StpUtil.isLogin()) {
            return null;
        }
        return StpUtil.getLoginIdAsString();
    }

    /**
     * 当前登录用户在「自己那张表」里的主键 id
     * 例如 "student:5" -> 5L
     */
    public static Long currentId() {
        String loginId = currentLoginId();
        if (loginId == null) {
            throw new RuntimeException("未登录！");
        }
        int idx = loginId.indexOf(':');
        String idPart = idx >= 0 ? loginId.substring(idx + 1) : loginId;
        return Long.valueOf(idPart);
    }

    /** 当前登录用户的角色（admin / student / teacher）；未登录返回 null */
    public static String currentRole() {
        String loginId = currentLoginId();
        if (loginId == null) {
            return null;
        }
        int idx = loginId.indexOf(':');
        return idx >= 0 ? loginId.substring(0, idx) : null;
    }
}
