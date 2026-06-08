package com.visionox.mes.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.visionox.mes.auth.dto.LoginRequest;
import com.visionox.mes.auth.dto.LoginResponse;
import com.visionox.mes.auth.entity.User;
import com.visionox.mes.auth.mapper.UserMapper;
import com.visionox.mes.auth.security.RolePermissionService;
import com.visionox.mes.auth.util.JwtUtil;
import com.visionox.mes.common.BusinessException;
import cn.hutool.crypto.digest.BCrypt;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 认证服务
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final RolePermissionService rolePermissionService;

    /**
     * 用户登录
     */
    public LoginResponse login(LoginRequest request) {
        // 查询用户
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, request.getUsername());
        User user = userMapper.selectOne(wrapper);

        if (user == null) {
            throw new BusinessException("用户名或密码错误");
        }

        if (!matchesPassword(request.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }

        // 检查状态
        if (user.getStatus() == 0) {
            throw new BusinessException("用户已被禁用");
        }

        String role = rolePermissionService.normalizeRole(user.getRole());
        String token = jwtUtil.generateToken(user.getUsername(), role);

        return new LoginResponse(token, user.getUsername(), user.getRealName(), role,
                rolePermissionService.permissions(role, user.getUsername()));
    }

    private boolean matchesPassword(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null) {
            return false;
        }
        if (storedPassword.startsWith("$2a$") || storedPassword.startsWith("$2b$") || storedPassword.startsWith("$2y$")) {
            try {
                return BCrypt.checkpw(rawPassword, storedPassword);
            } catch (Exception ignored) {
                return false;
            }
        }
        return storedPassword.equals(rawPassword);
    }
}
