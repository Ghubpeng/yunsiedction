package com.yunsie.module.certificate.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yunsie.common.api.PageResult;
import com.yunsie.common.exception.BizException;
import com.yunsie.module.certificate.api.CertificateDeletingEvent;
import com.yunsie.module.certificate.dto.CertCreateReq;
import com.yunsie.module.certificate.dto.CertUpdateReq;
import com.yunsie.module.certificate.entity.Certificate;
import com.yunsie.module.certificate.entity.CertificateCategory;
import com.yunsie.module.certificate.error.CertErrorCode;
import com.yunsie.module.certificate.mapper.CertificateCategoryMapper;
import com.yunsie.module.certificate.mapper.CertificateMapper;
import com.yunsie.module.certificate.service.CertificateService;
import com.yunsie.module.certificate.vo.CertVO;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 证书实现。删除前发布事件交由 subject 域做完整性否决（跨域只走契约/事件，无反向依赖）。
 */
@Service
@RequiredArgsConstructor
public class CertificateServiceImpl implements CertificateService {

    private final CertificateMapper certificateMapper;
    private final CertificateCategoryMapper categoryMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(CertCreateReq req) {
        requireCategory(req.categoryId());
        long count = certificateMapper.selectCount(
                new LambdaQueryWrapper<Certificate>().eq(Certificate::getCode, req.code()));
        if (count > 0) {
            throw new BizException(CertErrorCode.CERT_CODE_EXISTS);
        }
        Certificate cert = new Certificate();
        cert.setCategoryId(req.categoryId());
        cert.setName(req.name());
        cert.setCode(req.code());
        cert.setShortName(req.shortName() == null ? "" : req.shortName());
        cert.setDescription(req.description() == null ? "" : req.description());
        cert.setSort(req.sort() == null ? 0 : req.sort());
        cert.setEnabled(req.enabled() == null ? 1 : req.enabled());
        certificateMapper.insert(cert);
        return cert.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, CertUpdateReq req) {
        Certificate cert = requireCertificate(id);
        cert.setName(req.name());
        cert.setShortName(req.shortName());
        cert.setDescription(req.description());
        cert.setSort(req.sort());
        cert.setEnabled(req.enabled());
        certificateMapper.updateById(cert);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Certificate cert = requireCertificate(id);
        // 完整性否决：subject 域监听该事件，存在考试科目时抛业务异常回滚
        eventPublisher.publishEvent(new CertificateDeletingEvent(id));
        // 逻辑删除时释放自然键（uk(code, deleted)）：避免同码重复"创建→删除"循环撞唯一索引
        cert.setCode(cert.getCode() + "#d" + cert.getId());
        certificateMapper.updateById(cert);
        certificateMapper.deleteById(id);
    }

    @Override
    public CertVO get(Long id) {
        return toVO(requireCertificate(id));
    }

    @Override
    public PageResult<CertVO> page(int pageNum, int pageSize, Long categoryId, String keyword, Boolean enabled) {
        LambdaQueryWrapper<Certificate> wrapper = baseWrapper(categoryId, keyword);
        if (enabled != null) {
            wrapper.eq(Certificate::getEnabled, enabled ? 1 : 0);
        }
        wrapper.orderByAsc(Certificate::getSort).orderByAsc(Certificate::getId);
        return doPage(pageNum, pageSize, wrapper);
    }

    @Override
    public PageResult<CertVO> publicPage(int pageNum, int pageSize, Long categoryId, String keyword) {
        LambdaQueryWrapper<Certificate> wrapper = baseWrapper(categoryId, keyword);
        wrapper.eq(Certificate::getEnabled, 1);
        wrapper.orderByAsc(Certificate::getSort).orderByAsc(Certificate::getId);
        return doPage(pageNum, pageSize, wrapper);
    }

    @Override
    public CertVO publicGet(Long id) {
        Certificate cert = requireCertificate(id);
        if (cert.getEnabled() == null || cert.getEnabled() != 1) {
            throw new BizException(CertErrorCode.CERT_NOT_FOUND);
        }
        return toVO(cert);
    }

    private PageResult<CertVO> doPage(int pageNum, int pageSize, LambdaQueryWrapper<Certificate> wrapper) {
        Page<Certificate> page = certificateMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<Long> categoryIds = page.getRecords().stream().map(Certificate::getCategoryId).distinct().toList();
        Map<Long, String> categoryNames = categoryIds.isEmpty() ? Map.of()
                : categoryMapper.selectBatchIds(categoryIds).stream()
                        .collect(Collectors.toMap(CertificateCategory::getId, CertificateCategory::getName,
                                (a, b) -> a));
        List<CertVO> list = page.getRecords().stream().map(c -> toVO(c, categoryNames)).toList();
        return PageResult.of(list, page.getTotal());
    }

    private LambdaQueryWrapper<Certificate> baseWrapper(Long categoryId, String keyword) {
        LambdaQueryWrapper<Certificate> wrapper = new LambdaQueryWrapper<>();
        if (categoryId != null) {
            wrapper.eq(Certificate::getCategoryId, categoryId);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Certificate::getName, keyword)
                    .or().like(Certificate::getCode, keyword)
                    .or().like(Certificate::getShortName, keyword));
        }
        return wrapper;
    }

    private CertVO toVO(Certificate cert) {
        CertificateCategory category = categoryMapper.selectById(cert.getCategoryId());
        return new CertVO(cert.getId(), cert.getCategoryId(),
                category == null ? null : category.getName(),
                cert.getName(), cert.getCode(), cert.getShortName(), cert.getDescription(),
                cert.getEnabled(), cert.getSort(), cert.getCreateTime());
    }

    private CertVO toVO(Certificate cert, Map<Long, String> categoryNames) {
        return new CertVO(cert.getId(), cert.getCategoryId(),
                categoryNames.getOrDefault(cert.getCategoryId(), null),
                cert.getName(), cert.getCode(), cert.getShortName(), cert.getDescription(),
                cert.getEnabled(), cert.getSort(), cert.getCreateTime());
    }

    private Certificate requireCertificate(Long id) {
        Certificate cert = certificateMapper.selectById(id);
        if (cert == null) {
            throw new BizException(CertErrorCode.CERT_NOT_FOUND);
        }
        return cert;
    }

    private void requireCategory(Long categoryId) {
        if (categoryMapper.selectById(categoryId) == null) {
            throw new BizException(CertErrorCode.CERT_CATEGORY_NOT_FOUND);
        }
    }
}
