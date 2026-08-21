package com.yunsie.module.sys.vo;

/**
 * 客服配置视图（公开/管理共用；字段全为配置值，无敏感数据）。
 */
public record CustomerServiceVO(
        String phone,
        String wechat,
        String qrCodeUrl,
        String serviceTime,
        Integer enabled) {
}
