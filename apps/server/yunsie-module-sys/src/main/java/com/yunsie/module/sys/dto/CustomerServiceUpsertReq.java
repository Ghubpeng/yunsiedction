package com.yunsie.module.sys.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 客服配置更新入参（管理端）。
 */
public record CustomerServiceUpsertReq(
        @Size(max = 30, message = "电话最长30字符") String phone,
        @Size(max = 50, message = "微信号最长50字符") String wechat,
        @Size(max = 500, message = "二维码URL最长500字符") String qrCodeUrl,
        @Size(max = 100, message = "服务时间最长100字符") String serviceTime,
        @NotNull(message = "开关不能为空") Integer enabled) {
}
