package com.yunsie.module.course.service;

import com.yunsie.common.exception.BizException;
import com.yunsie.module.course.error.CourseErrorCode;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;

/**
 * MinIO 存储服务（course 域）：上传 / 预签名播放凭证 / 存在性检查 / 桶初始化。
 * 视频原文件直存，不转码（MVP）；播放凭证短有效期，服务端鉴权后才能获取。
 */
@Slf4j
@Service
public class MinioStorageService {

    /** SDK 操作客户端（上传/管理；容器部署指向 compose 内网地址） */
    private final MinioClient minioClient;

    /** 预签名播放 URL 专用客户端（浏览器可达地址签名；S3 签名包含 Host，必须分开） */
    private final MinioClient presignClient;

    public MinioStorageService(MinioClient minioClient,
                               @Qualifier("minioPresignClient") MinioClient presignClient) {
        this.minioClient = minioClient;
        this.presignClient = presignClient;
    }

    @Value("${yunsie.minio.bucket:yunsie-videos}")
    private String bucket;

    /** 上传对象（桶不存在时自动创建） */
    public void upload(String objectKey, InputStream input, long size, String contentType) {
        ensureBucket();
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(input, size, -1)
                    .contentType(contentType == null ? "application/octet-stream" : contentType)
                    .build());
        } catch (Exception e) {
            log.error("minio upload failed, object={}", objectKey, e);
            throw new BizException(CourseErrorCode.VIDEO_UPLOAD_FAILED);
        }
    }

    /** 短有效期预签名下载 URL（播放凭证） */
    public String presignedGetUrl(String objectKey, int expirySeconds) {
        try {
            return presignClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(objectKey)
                    .expiry(expirySeconds)
                    .build());
        } catch (Exception e) {
            log.error("minio presigned url failed, object={}", objectKey, e);
            throw new BizException(CourseErrorCode.VIDEO_UPLOAD_FAILED);
        }
    }

    /** 对象是否存在 */
    public boolean exists(String objectKey) {
        try {
            minioClient.statObject(StatObjectArgs.builder().bucket(bucket).object(objectKey).build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 删除对象（尽力而为：失败仅记日志，不阻断主流程） */
    public void removeQuietly(String objectKey) {
        try {
            minioClient.removeObject(io.minio.RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception e) {
            log.warn("minio remove object failed, object={}", objectKey, e);
        }
    }

    public String bucketName() {
        return bucket;
    }

    private void ensureBucket() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception e) {
            log.error("minio ensure bucket failed, bucket={}", bucket, e);
            throw new BizException(CourseErrorCode.VIDEO_UPLOAD_FAILED);
        }
    }
}
