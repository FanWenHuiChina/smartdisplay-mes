package com.visionox.mes.auth.security;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 当前请求认证用户。
 */
@Data
@AllArgsConstructor
public class AuthUser {

    private String username;

    private String role;
}
