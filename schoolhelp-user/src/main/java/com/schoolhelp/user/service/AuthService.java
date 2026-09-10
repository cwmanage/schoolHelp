package com.schoolhelp.user.service;

import com.schoolhelp.common.exception.BusinessException;
import com.schoolhelp.common.util.JwtUtil;
import com.schoolhelp.user.dto.ChangePasswordDTO;
import com.schoolhelp.user.dto.LoginDTO;
import com.schoolhelp.user.dto.RegisterDTO;
import com.schoolhelp.user.dto.UpdateProfileDTO;
import com.schoolhelp.user.entity.User;
import com.schoolhelp.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证 + 用户资料服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    // ---------------- 注册 ----------------
    public Map<String, Object> register(RegisterDTO dto) {
        // 二次确认：两次密码一致
        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            throw new BusinessException(400, "两次输入的密码不一致");
        }
        // 用户名唯一
        Long count = userMapper.selectCount(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<User>lambdaQuery()
                        .eq(User::getUsername, dto.getUsername()));
        if (count != null && count > 0) {
            throw new BusinessException(400, "用户名已被注册");
        }
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(encoder.encode(dto.getPassword()));
        user.setNickname(StringUtils.hasText(dto.getNickname()) ? dto.getNickname() : dto.getUsername());
        user.setSchoolId(dto.getSchoolId());
        user.setCollege(dto.getCollege());
        user.setClassName(dto.getClassName());
        // 角色安全：只允许注册为同学(0)或班长(1)；管理员(2)不允许注册接口创建
        int role = dto.getRole() == null ? 0 : dto.getRole();
        if (role == com.schoolhelp.common.constant.CommonConstants.ROLE_ADMIN) {
            throw new BusinessException(400, "管理员账号由系统创建，不可注册");
        }
        if (role != com.schoolhelp.common.constant.CommonConstants.ROLE_MONITOR) {
            role = com.schoolhelp.common.constant.CommonConstants.ROLE_STUDENT;
        }
        user.setRole(role);
        user.setAvatarType(0);
        user.setAvatarUrl(null);
        user.setStatus(1);
        userMapper.insert(user);
        log.info("新用户注册: id={}, username={}, role={}", user.getId(), user.getUsername(), role);

        Map<String, Object> map = new HashMap<>();
        map.put("userId", user.getId());
        map.put("username", user.getUsername());
        return map;
    }

    // ---------------- 登录 ----------------
    public Map<String, Object> login(LoginDTO dto) {
        User user = userMapper.selectOne(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<User>lambdaQuery()
                        .eq(User::getUsername, dto.getUsername()));
        if (user == null || !encoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException(400, "用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(403, "账号已被禁用，请联系管理员");
        }
        String token = JwtUtil.createToken(user.getId(), user.getUsername(), user.getRole());

        Map<String, Object> map = new HashMap<>();
        map.put("token", token);
        map.put("user", buildUserVO(user));
        return map;
    }

    // ---------------- 个人中心 ----------------
    public Map<String, Object> profile(Long userId) {
        User user = requireUser(userId);
        return buildUserVO(user);
    }

    public void updateProfile(Long userId, UpdateProfileDTO dto) {
        User user = requireUser(userId);
        if (StringUtils.hasText(dto.getNickname())) user.setNickname(dto.getNickname());
        if (dto.getSchoolId() != null) user.setSchoolId(dto.getSchoolId());
        if (dto.getCollege() != null) user.setCollege(dto.getCollege());
        if (dto.getClassName() != null) user.setClassName(dto.getClassName());
        user.setSignature(dto.getSignature());
        userMapper.updateById(user);
    }

    // ---------------- 修改密码（二次确认 + 强度校验提示） ----------------
    public void changePassword(Long userId, ChangePasswordDTO dto) {
        User user = requireUser(userId);
        if (!encoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BusinessException(400, "原密码错误");
        }
        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new BusinessException(400, "两次输入的新密码不一致");
        }
        // 密码强度已由 ChangePasswordDTO 的 @Size(8-32) + @Pattern(字母+数字) 强制校验
        int level = com.schoolhelp.common.util.PasswordStrengthUtil.level(dto.getNewPassword());
        user.setPassword(encoder.encode(dto.getNewPassword()));
        userMapper.updateById(user);
        log.info("用户修改密码: id={}, 强度={}", userId, level);
    }

    /** 用户名查重 */
    public Long checkUsernameExists(String username) {
        return userMapper.selectCount(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<User>lambdaQuery()
                        .eq(User::getUsername, username));
    }

    // ---------------- 内部 ----------------
    public User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        return user;
    }

    /** 脱敏 VO：不返回密码 */
    public Map<String, Object> buildUserVO(User user) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", user.getId());
        m.put("username", user.getUsername());
        m.put("nickname", user.getNickname());
        m.put("role", user.getRole());
        m.put("schoolId", user.getSchoolId());
        m.put("college", user.getCollege());
        m.put("className", user.getClassName());
        m.put("signature", user.getSignature());
        m.put("avatarType", user.getAvatarType());
        m.put("avatarUrl", user.getAvatarUrl());
        m.put("status", user.getStatus());
        return m;
    }
}
