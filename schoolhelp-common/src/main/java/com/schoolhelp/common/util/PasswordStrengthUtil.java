package com.schoolhelp.common.util;

/**
 * 密码强度工具：按规则分四档
 * 弱0 / 中1 / 强2 / 极强3
 */
public class PasswordStrengthUtil {

    /** 评分 0-4：长度、小写、大写、数字、符号 各1分 */
    public static int score(String pwd) {
        if (pwd == null || pwd.isEmpty()) return 0;
        int score = 0;
        if (pwd.length() >= 8) score++;
        if (pwd.matches(".*[a-z].*")) score++;
        if (pwd.matches(".*[A-Z].*")) score++;
        if (pwd.matches(".*\\d.*")) score++;
        if (pwd.matches(".*[^a-zA-Z0-9].*")) score++;
        return score;
    }

    /**
     * 强度档位
     * @return 0弱 1中 2强 3极强
     */
    public static int level(String pwd) {
        int s = score(pwd);
        if (s <= 2) return 0;      // 弱
        if (s == 3) return 1;      // 中
        if (s == 4) return 2;      // 强
        return 3;                  // 极强
    }

    public static String levelName(String pwd) {
        return switch (level(pwd)) {
            case 0 -> "弱";
            case 1 -> "中";
            case 2 -> "强";
            default -> "极强";
        };
    }

    /** 推荐的理想密码格式提示文案 */
    public static String suggestion() {
        return "建议至少8位，包含大小写字母、数字和特殊符号，如: Abc@2026school";
    }
}
