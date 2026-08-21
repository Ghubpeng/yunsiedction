package com.yunsie.boot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MinIO 配置（yunsie.minio.*）。开发默认值为本地容器占位，生产必须经环境变量注入（无真实密钥入库）。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "yunsie.minio")
public class MinioProperties {

    /** 服务地址（SDK 上传/管理用；容器部署为 compose 内网地址 http://minio:9000） */
    private String endpoint = "http://localhost:9000";

    /**
     * 浏览器可达的对外地址（预签名播放 URL 使用；容器部署必须为宿主可达地址，
     * 否则预签名 URL 主机为容器内网 DNS，浏览器无法播放）。空 = 与 endpoint 一致（本地开发默认）。
     */
    private String publicEndpoint = "";

    /**
     * 区域：显式指定后预签名不发起 region 发现网络请求（容器内 public-endpoint 可能不可达，
     * 如 localhost 指向容器自身）；MinIO 默认区域 us-east-1。
     */
    private String region = "us-east-1";

    /** 访问密钥（仅开发占位） */
    private String accessKey = "minioadmin";

    /** 私密密钥（仅开发占位） */
    private String secretKey = "minioadmin";

    /** 视频桶名 */
    private String bucket = "yunsie-videos";
}
