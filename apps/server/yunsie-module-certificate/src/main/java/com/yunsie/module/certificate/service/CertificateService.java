package com.yunsie.module.certificate.service;

import com.yunsie.common.api.PageResult;
import com.yunsie.module.certificate.dto.CertCreateReq;
import com.yunsie.module.certificate.dto.CertUpdateReq;
import com.yunsie.module.certificate.vo.CertVO;

/**
 * 证书服务。
 */
public interface CertificateService {

    Long create(CertCreateReq req);

    void update(Long id, CertUpdateReq req);

    /** 删除前发布 {@code CertificateDeletingEvent}，由 subject 域否决（存在科目时禁止删除） */
    void delete(Long id);

    CertVO get(Long id);

    PageResult<CertVO> page(int pageNum, int pageSize, Long categoryId, String keyword, Boolean enabled);

    /** 用户公开端：仅启用证书 */
    PageResult<CertVO> publicPage(int pageNum, int pageSize, Long categoryId, String keyword);

    CertVO publicGet(Long id);
}
