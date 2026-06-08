package com.visionox.mes.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

/**
 * 登录响应
 */
@Data
@AllArgsConstructor
public class LoginResponse {

    /**
     * JWT Token
     */
    private String token;

    /**
     * 用户名
     */
    private String username;

    /**
     * 真实姓名
     */
    private String realName;

    /**
     * 角色
     */
    private String role;

    /**
     * 菜单、按钮和数据范围权限。
     */
    private Map<String, Object> permissions;
}
