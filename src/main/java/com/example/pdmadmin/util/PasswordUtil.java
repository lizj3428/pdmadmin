package com.example.pdmadmin.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码工具类
 *
 * 【作用】全项目唯一的密码加密 / 校验入口。
 *        数据库里的 password 列只存 BCrypt 密文，永远不出现明文。
 *
 * 【为什么用 BCrypt（答辩可讲）】
 * 1. 自带随机盐：同一个密码每次加密结果都不同，彩虹表（预计算哈希表）直接失效；
 * 2. 故意算得慢：单次校验约几十毫秒，攻击者暴力猜测的成本被放大成千上万倍；
 * 3. 密文自带算法标识 / 强度 / 盐，所以数据库只需要一列 password，
 *    不需要额外设计 salt 字段。
 *
 * 【为什么还要兼容明文】
 * 老库里的账号是明文存的。如果直接改成只认 BCrypt，那些人全都登不进来。
 * 所以 matches() 做了一个兜底：库里的串不是 BCrypt 格式时，按明文比对一次；
 * 比对成功后由登录接口把它改写成 BCrypt（自动升级，只发生一次）。
 */
public class PasswordUtil {

    /** BCrypt 强度 10（默认值，2^10 次迭代，兼顾安全与登录速度） */
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private PasswordUtil() {
    }

    /**
     * 明文 -> BCrypt 密文
     * @return 空值返回 null（交给调用方判断是否合法，避免把空串加密成合法密文）
     */
    public static String encode(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        return ENCODER.encode(raw);
    }

    /**
     * 校验明文密码是否与库里存的串匹配
     *
     * @param raw    用户输入的明文密码
     * @param stored 数据库 password 列的值
     */
    public static boolean matches(String raw, String stored) {
        if (raw == null || stored == null || stored.isEmpty()) {
            return false;
        }
        if (isEncoded(stored)) {
            try {
                return ENCODER.matches(raw, stored);
            } catch (IllegalArgumentException e) {
                // 串是 $2x$ 开头但内容损坏，当作不匹配，不能让它把登录接口打成 500
                return false;
            }
        }
        // 兼容历史明文数据
        return stored.equals(raw);
    }

    /**
     * 判断这串是否已经是 BCrypt 密文
     * BCrypt 密文固定以 $2a$ / $2b$ / $2y$ 开头，总长 60
     */
    public static boolean isEncoded(String stored) {
        if (stored == null) {
            return false;
        }
        return stored.startsWith("$2a$") || stored.startsWith("$2b$") || stored.startsWith("$2y$");
    }
}
