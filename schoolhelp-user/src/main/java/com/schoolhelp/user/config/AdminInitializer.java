package com.schoolhelp.user.config;

import com.schoolhelp.common.constant.CommonConstants;
import com.schoolhelp.user.entity.User;
import com.schoolhelp.user.mapper.UserMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 默认管理员初始化：首次启动自动创建 admin/admin123（幂等，已存在则跳过）
 * 管理员账号不允许通过注册接口创建，只能由本初始化器生成
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer implements ApplicationRunner {

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public void run(ApplicationArguments args) {
        String username = CommonConstants.DEFAULT_ADMIN_USERNAME;
        Long count = userMapper.selectCount(
                Wrappers.<User>lambdaQuery().eq(User::getUsername, username));
        if (count != null && count > 0) {
            log.info("默认管理员 {} 已存在，跳过创建", username);
            return;
        }
        User admin = new User();
        admin.setUsername(username);
        admin.setPassword(encoder.encode(CommonConstants.DEFAULT_ADMIN_PASSWORD));
        admin.setNickname("系统管理员");
        admin.setRole(CommonConstants.ROLE_ADMIN);
        admin.setAvatarType(0);
        admin.setStatus(1);
        admin.setApproveStatus(1);
        userMapper.insert(admin);
        log.warn("默认管理员已创建: username={}, 默认密码={}，请尽快登录修改密码",
                username, CommonConstants.DEFAULT_ADMIN_PASSWORD);
    }
}
