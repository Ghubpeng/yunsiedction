package com.yunsie.module.certificate.api;

/**
 * 证书域查询契约（对外 API）。
 * 供 subject/course/question/exam/ai 等域使用；
 * 其他域禁止直连 certificate 域 Entity/Mapper/表（模块边界铁律）。
 */
public interface CertificateQueryApi {

    /** 查询证书（含禁用态；null=不存在或已逻辑删除） */
    CertificateView findCertificate(Long certificateId);

    /** 证书存在且启用 */
    boolean existsEnabled(Long certificateId);

    /** 查询分类 */
    CategoryView findCategory(Long categoryId);

    /** 证书总数（含禁用；Stage 2.3B 运营看板） */
    long countAll();

    /** 启用证书列表（Stage 2.3B 运营看板/无课程章节扫描） */
    java.util.List<CertificateView> listEnabled();

    record CertificateView(Long id, Long categoryId, String name, String code, String shortName, Integer enabled) {
    }

    record CategoryView(Long id, String name, String code, Integer enabled) {
    }
}
