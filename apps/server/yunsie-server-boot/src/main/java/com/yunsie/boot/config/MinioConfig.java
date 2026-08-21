package com.yunsie.boot.config;

import io.minio.MinioClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;

/**
 * MinIO 装配：
 *   - minioClient（@Primary）：SDK 上传/管理（容器部署指向 compose 内网地址）。
 *   - minioPresignClient：预签名播放 URL 专用（指向浏览器可达的 public-endpoint；
 *     S3 签名包含 Host，必须用对外地址单独签名）。本地开发两者一致。
 * MinIO 服务已在 dev compose 中运行（不新增基础设施）；本阶段仅引入官方 Java SDK。
 */
@Configuration
@EnableConfigurationProperties(MinioProperties.class)
public class MinioConfig {

    @Bean
    @Primary
    public MinioClient minioClient(MinioProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .region(properties.getRegion())
                .build();
    }

    /** 预签名客户端：public-endpoint 为空时回退 endpoint（本地开发行为不变） */
    @Bean("minioPresignClient")
    public MinioClient minioPresignClient(MinioProperties properties) {
        return MinioClient.builder()
                .endpoint(resolvePresignEndpoint(properties))
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                // 显式 region：预签名仅本地计算，不向 endpoint 发起 region 发现请求
                // （容器内 public-endpoint 不可达时依然可用）
                .region(properties.getRegion())
                .build();
    }

    /** 预签名端点选择逻辑（包内可见，便于单元测试） */
    static String resolvePresignEndpoint(MinioProperties properties) {
        return StringUtils.hasText(properties.getPublicEndpoint())
                ? properties.getPublicEndpoint()
                : properties.getEndpoint();
    }
}
