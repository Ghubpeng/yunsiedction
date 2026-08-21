package com.yunsie.boot.config;

import io.minio.MinioClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * MinIO 客户端装配单元测试（不依赖 Spring 上下文）。
 * 核心回归点：容器部署时预签名 URL 必须使用浏览器可达的 public-endpoint，
 * 本地开发（public-endpoint 为空）行为不变（回退 endpoint）。
 */
class MinioConfigTest {

    private static MinioProperties props(String endpoint, String publicEndpoint) {
        MinioProperties p = new MinioProperties();
        p.setEndpoint(endpoint);
        p.setPublicEndpoint(publicEndpoint);
        return p;
    }

    @Test
    void resolvePresignEndpoint_usesPublicEndpointWhenSet() {
        MinioProperties p = props("http://minio:9000", "http://localhost:9000");
        assertEquals("http://localhost:9000", MinioConfig.resolvePresignEndpoint(p));
    }

    @Test
    void resolvePresignEndpoint_fallsBackToEndpointWhenPublicBlank() {
        MinioProperties p = props("http://localhost:9000", "");
        assertEquals("http://localhost:9000", MinioConfig.resolvePresignEndpoint(p));
        MinioProperties whitespace = props("http://localhost:9000", "   ");
        assertEquals("http://localhost:9000", MinioConfig.resolvePresignEndpoint(whitespace));
    }

    @Test
    void createsTwoDistinctClients() {
        MinioConfig config = new MinioConfig();
        MinioProperties p = props("http://minio:9000", "http://localhost:9000");
        MinioClient internal = config.minioClient(p);
        MinioClient presign = config.minioPresignClient(p);
        assertNotNull(internal);
        assertNotNull(presign);
        // 两个客户端实例必须独立（各自 endpoint 不同，签名 Host 不同）
        assertNotSame(internal, presign);
    }
}
