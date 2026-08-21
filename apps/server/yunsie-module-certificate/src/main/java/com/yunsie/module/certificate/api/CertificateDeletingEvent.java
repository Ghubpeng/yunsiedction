package com.yunsie.module.certificate.api;

/**
 * 证书删除前事件（subject 域监听用于数据完整性否决：存在考试科目时禁止删除）。
 * 放在 api 包以便 subject 域订阅；依赖方向保持 certificate ← subject，无环。
 */
public record CertificateDeletingEvent(Long certificateId) {
}
