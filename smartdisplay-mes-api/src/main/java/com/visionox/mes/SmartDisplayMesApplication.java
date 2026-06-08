package com.visionox.mes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SmartDisplay MES 应用启动类
 *
 * 项目说明：
 * - 参考显示行业公开资料与通用 MES 模型构建的制造执行系统
 * - 使用Spring Boot 3 + MyBatis-Plus + PostgreSQL
 * - 核心功能：Lot流转控制、Recipe管理、质量追溯、Hold/Release
 *
 * @author SmartDisplay MES
 * @since 2024-06
 */
@SpringBootApplication
public class SmartDisplayMesApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartDisplayMesApplication.class, args);
        System.out.println("\n========================================");
        System.out.println("SmartDisplay MES 启动成功！");
        System.out.println("Swagger UI: http://localhost:8080/api/swagger-ui.html");
        System.out.println("========================================\n");
    }
}
