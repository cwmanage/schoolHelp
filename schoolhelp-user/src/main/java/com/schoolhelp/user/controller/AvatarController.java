package com.schoolhelp.user.controller;

import com.schoolhelp.common.exception.BusinessException;
import com.schoolhelp.common.result.Result;
import com.schoolhelp.common.util.UserContext;
import com.schoolhelp.user.entity.User;
import com.schoolhelp.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 头像上传（开发期存本地 upload-dir，部署改配置指向 NAS）
 */
@Slf4j
@RestController
@RequestMapping("/profile")
@RequiredArgsConstructor
public class AvatarController {

    private final UserMapper userMapper;

    @Value("${schoolhelp.file.upload-dir}")
    private String uploadDir;

    @Value("${schoolhelp.file.base-url:/files}")
    private String baseUrl;

    private static final long MAX_SIZE = 5 * 1024 * 1024; // 5MB
    private static final String[] ALLOWED_EXT = {"jpg", "jpeg", "png", "gif", "webp"};

    @PostMapping("/avatar")
    public Result<Map<String, Object>> uploadAvatar(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "请选择图片文件");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException(400, "图片不能超过5MB");
        }
        String original = file.getOriginalFilename();
        String ext = original == null ? "" :
                original.contains(".") ? original.substring(original.lastIndexOf('.') + 1).toLowerCase() : "";
        boolean allowed = false;
        for (String a : ALLOWED_EXT) {
            if (a.equals(ext)) { allowed = true; break; }
        }
        if (!allowed) {
            throw new BusinessException(400, "仅支持 jpg/png/gif/webp 图片");
        }

        String dir = uploadDir + "/avatar";
        try {
            Files.createDirectories(Paths.get(dir));
        } catch (IOException e) {
            throw new BusinessException(500, "创建上传目录失败");
        }
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path target = Paths.get(dir, filename);
        try {
            file.transferTo(target.toAbsolutePath());
        } catch (IOException e) {
            log.error("头像保存失败", e);
            throw new BusinessException(500, "头像保存失败");
        }

        // 更新用户头像
        Long userId = UserContext.getUserId();
        User user = userMapper.selectById(userId);
        if (user != null) {
            user.setAvatarType(1);
            user.setAvatarUrl(baseUrl + "/avatar/" + filename);
            userMapper.updateById(user);
        }

        Map<String, Object> map = new HashMap<>();
        map.put("avatarType", 1);
        map.put("avatarUrl", baseUrl + "/avatar/" + filename);
        return Result.ok(map);
    }
}
