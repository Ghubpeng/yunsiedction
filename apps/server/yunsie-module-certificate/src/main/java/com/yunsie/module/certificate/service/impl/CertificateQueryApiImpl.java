package com.yunsie.module.certificate.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunsie.module.certificate.api.CertificateQueryApi;
import com.yunsie.module.certificate.entity.Certificate;
import com.yunsie.module.certificate.entity.CertificateCategory;
import com.yunsie.module.certificate.mapper.CertificateCategoryMapper;
import com.yunsie.module.certificate.mapper.CertificateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 证书域查询契约实现。
 */
@Service
@RequiredArgsConstructor
public class CertificateQueryApiImpl implements CertificateQueryApi {

    private final CertificateMapper certificateMapper;
    private final CertificateCategoryMapper categoryMapper;

    @Override
    public CertificateView findCertificate(Long certificateId) {
        Certificate c = certificateMapper.selectById(certificateId);
        if (c == null) {
            return null;
        }
        return new CertificateView(c.getId(), c.getCategoryId(), c.getName(), c.getCode(),
                c.getShortName(), c.getEnabled());
    }

    @Override
    public boolean existsEnabled(Long certificateId) {
        Certificate c = certificateMapper.selectById(certificateId);
        return c != null && c.getEnabled() != null && c.getEnabled() == 1;
    }

    @Override
    public CategoryView findCategory(Long categoryId) {
        CertificateCategory c = categoryMapper.selectById(categoryId);
        if (c == null) {
            return null;
        }
        return new CategoryView(c.getId(), c.getName(), c.getCode(), c.getEnabled());
    }

    @Override
    public long countAll() {
        Long count = certificateMapper.selectCount(new LambdaQueryWrapper<Certificate>());
        return count == null ? 0 : count;
    }

    @Override
    public java.util.List<CertificateView> listEnabled() {
        return certificateMapper.selectList(new LambdaQueryWrapper<Certificate>()
                        .eq(Certificate::getEnabled, 1)
                        .orderByAsc(Certificate::getSort).orderByAsc(Certificate::getId))
                .stream()
                .map(c -> new CertificateView(c.getId(), c.getCategoryId(), c.getName(), c.getCode(),
                        c.getShortName(), c.getEnabled()))
                .toList();
    }
}
