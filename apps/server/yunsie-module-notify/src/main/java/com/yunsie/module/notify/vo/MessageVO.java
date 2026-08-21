package com.yunsie.module.notify.vo;

import java.time.LocalDateTime;

/**
 * 站内消息视图（本人视角）。
 */
public record MessageVO(
        Long id,
        String title,
        String content,
        Integer messageType,
        Long bizId,
        Integer isRead,
        LocalDateTime readTime,
        LocalDateTime createTime) {
}
