package com.schoolhelp.user.controller;

import com.schoolhelp.common.dto.ReviewDTO;
import com.schoolhelp.common.result.Result;
import com.schoolhelp.common.util.UserContext;
import com.schoolhelp.user.dto.ChangePasswordDTO;
import com.schoolhelp.user.dto.LoginDTO;
import com.schoolhelp.user.dto.RegisterDTO;
import com.schoolhelp.user.dto.UpdateProfileDTO;
import com.schoolhelp.user.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证 + 用户接口（网关前缀 /api/user，转发后为 /auth /profile）
 */
@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 注册 */
    @PostMapping("/auth/register")
    public Result<Map<String, Object>> register(@Valid @RequestBody RegisterDTO dto) {
        return Result.ok(authService.register(dto));
    }

    /** 登录 */
    @PostMapping("/auth/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginDTO dto) {
        return Result.ok(authService.login(dto));
    }

    /** 密码强度提示（不强制） */
    @GetMapping("/auth/password-strength")
    public Result<Map<String, String>> passwordStrength(@RequestParam String password) {
        int level = com.schoolhelp.common.util.PasswordStrengthUtil.level(password);
        Map<String, String> m = new java.util.HashMap<>();
        m.put("level", String.valueOf(level));
        m.put("levelName", com.schoolhelp.common.util.PasswordStrengthUtil.levelName(password));
        m.put("suggestion", com.schoolhelp.common.util.PasswordStrengthUtil.suggestion());
        return Result.ok(m);
    }

    /** 用户名查重 */
    @GetMapping("/auth/check-username")
    public Result<Boolean> checkUsername(@RequestParam String username) {
        Long count = authService.checkUsernameExists(username);
        return Result.ok(count != null && count > 0);
    }

    /** 班长审批：待审批列表（仅管理员） */
    @GetMapping("/admin/monitors/pending")
    public Result<java.util.List<java.util.Map<String, Object>>> pendingMonitors() {
        return Result.ok(authService.pendingMonitors(UserContext.getRole()));
    }

    /** 班长审批：通过/驳回（仅管理员） */
    @PostMapping("/admin/monitors/{id}/review")
    public Result<Void> reviewMonitor(@PathVariable Long id, @Valid @RequestBody ReviewDTO dto) {
        authService.reviewMonitor(id, dto, UserContext.getRole());
        return Result.ok();
    }

    /** 个人中心 */
    @GetMapping("/profile")
    public Result<Map<String, Object>> profile() {
        return Result.ok(authService.profile(UserContext.getUserId()));
    }

    /** 更新个人资料（昵称/签名/学号/院系班级） */
    @PutMapping("/profile")
    public Result<Void> updateProfile(@Valid @RequestBody UpdateProfileDTO dto) {
        authService.updateProfile(UserContext.getUserId(), dto);
        return Result.ok();
    }

    /** 修改密码（二次确认；密码强度由 DTO 强校验：8-32位且含字母和数字） */
    @PutMapping("/profile/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordDTO dto) {
        authService.changePassword(UserContext.getUserId(), dto);
        return Result.ok();
    }
}
