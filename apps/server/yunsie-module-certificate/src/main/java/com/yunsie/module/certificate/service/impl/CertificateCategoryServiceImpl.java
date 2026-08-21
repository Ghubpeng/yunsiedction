package com.yunsie.module.certificate.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunsie.common.exception.BizException;
import com.yunsie.module.certificate.dto.CategoryCreateReq;
import com.yunsie.module.certificate.dto.CategoryMoveReq;
import com.yunsie.module.certificate.dto.CategoryUpdateReq;
import com.yunsie.module.certificate.entity.Certificate;
import com.yunsie.module.certificate.entity.CertificateCategory;
import com.yunsie.module.certificate.error.CertErrorCode;
import com.yunsie.module.certificate.mapper.CertificateCategoryMapper;
import com.yunsie.module.certificate.mapper.CertificateMapper;
import com.yunsie.module.certificate.service.CertificateCategoryService;
import com.yunsie.module.certificate.vo.CategoryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 证书分类实现。path 以节点 id 构建（/1/2/），移动时整棵子树级联重写。
 */
@Service
@RequiredArgsConstructor
public class CertificateCategoryServiceImpl implements CertificateCategoryService {

    private final CertificateCategoryMapper categoryMapper;
    private final CertificateMapper certificateMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(CategoryCreateReq req) {
        long count = categoryMapper.selectCount(
                new LambdaQueryWrapper<CertificateCategory>().eq(CertificateCategory::getCode, req.code()));
        if (count > 0) {
            throw new BizException(CertErrorCode.CATEGORY_CODE_EXISTS);
        }
        CertificateCategory parent = null;
        if (req.parentId() != null && req.parentId() != 0) {
            parent = requireCategory(req.parentId());
        }
        CertificateCategory category = new CertificateCategory();
        category.setParentId(parent == null ? 0L : parent.getId());
        category.setName(req.name());
        category.setCode(req.code());
        category.setSort(req.sort() == null ? 0 : req.sort());
        category.setEnabled(req.enabled() == null ? 1 : req.enabled());
        category.setDescription(req.description() == null ? "" : req.description());
        category.setLevel(parent == null ? 1 : parent.getLevel() + 1);
        categoryMapper.insert(category);
        // 回填物化路径（依赖自增主键）：根为 /id/，子节点为 父路径+id/
        category.setPath((parent == null ? "/" : parent.getPath()) + category.getId() + "/");
        categoryMapper.updateById(category);
        return category.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, CategoryUpdateReq req) {
        CertificateCategory category = requireCategory(id);
        category.setName(req.name());
        category.setSort(req.sort());
        category.setEnabled(req.enabled());
        category.setDescription(req.description());
        categoryMapper.updateById(category);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        CertificateCategory category = requireCategory(id);
        long children = categoryMapper.selectCount(
                new LambdaQueryWrapper<CertificateCategory>().eq(CertificateCategory::getParentId, id));
        if (children > 0) {
            throw new BizException(CertErrorCode.CATEGORY_HAS_CHILDREN);
        }
        long certs = certificateMapper.selectCount(
                new LambdaQueryWrapper<Certificate>().eq(Certificate::getCategoryId, id));
        if (certs > 0) {
            throw new BizException(CertErrorCode.CATEGORY_HAS_CERTIFICATES);
        }
        // 逻辑删除时释放自然键（uk(code, deleted)）：避免同码重复"创建→删除"循环撞唯一索引
        category.setCode(category.getCode() + "#d" + category.getId());
        categoryMapper.updateById(category);
        categoryMapper.deleteById(id);
    }

    @Override
    public List<CategoryVO> tree() {
        return buildTree(listAll(), false);
    }

    @Override
    public List<CategoryVO> enabledTree() {
        return buildTree(listAll(), true);
    }

    @Override
    public List<CategoryVO> children(Long id) {
        requireCategory(id);
        return categoryMapper.selectList(new LambdaQueryWrapper<CertificateCategory>()
                        .eq(CertificateCategory::getParentId, id)
                        .orderByAsc(CertificateCategory::getSort).orderByAsc(CertificateCategory::getId))
                .stream().map(c -> toVO(c, List.of())).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void move(Long id, CategoryMoveReq req) {
        CertificateCategory node = requireCategory(id);
        String oldPath = node.getPath();
        int oldLevel = node.getLevel();

        String newParentPath;
        int newLevel;
        if (req.newParentId() == null || req.newParentId() == 0) {
            newParentPath = "/";
            newLevel = 1;
        } else {
            CertificateCategory newParent = requireCategory(req.newParentId());
            if (newParent.getId().equals(node.getId()) || newParent.getPath().startsWith(node.getPath())) {
                throw new BizException(CertErrorCode.CATEGORY_MOVE_INVALID);
            }
            newParentPath = newParent.getPath();
            newLevel = newParent.getLevel() + 1;
        }

        String newPath = newParentPath + node.getId() + "/";
        int levelDelta = newLevel - oldLevel;

        node.setParentId(req.newParentId() == null ? 0L : req.newParentId());
        node.setPath(newPath);
        node.setLevel(newLevel);
        if (req.sort() != null) {
            node.setSort(req.sort());
        }
        categoryMapper.updateById(node);

        // 子树级联：path 前缀替换 + level 平移
        List<CertificateCategory> descendants = categoryMapper.selectList(
                new LambdaQueryWrapper<CertificateCategory>()
                        .likeRight(CertificateCategory::getPath, oldPath));
        for (CertificateCategory d : descendants) {
            if (d.getId().equals(node.getId())) {
                continue;
            }
            d.setPath(newPath + d.getPath().substring(oldPath.length()));
            d.setLevel(d.getLevel() + levelDelta);
            categoryMapper.updateById(d);
        }
    }

    private List<CertificateCategory> listAll() {
        return categoryMapper.selectList(new LambdaQueryWrapper<CertificateCategory>()
                .orderByAsc(CertificateCategory::getSort).orderByAsc(CertificateCategory::getId));
    }

    private List<CategoryVO> buildTree(List<CertificateCategory> all, boolean enabledOnly) {
        List<CertificateCategory> visible = enabledOnly
                ? all.stream().filter(c -> c.getEnabled() != null && c.getEnabled() == 1).toList()
                : all;
        Map<Long, List<CertificateCategory>> byParent = visible.stream()
                .collect(Collectors.groupingBy(CertificateCategory::getParentId));
        return build(0L, byParent);
    }

    private List<CategoryVO> build(Long parentId, Map<Long, List<CertificateCategory>> byParent) {
        return byParent.getOrDefault(parentId, List.of()).stream()
                .map(c -> toVO(c, build(c.getId(), byParent)))
                .toList();
    }

    private CategoryVO toVO(CertificateCategory c, List<CategoryVO> children) {
        return new CategoryVO(c.getId(), c.getParentId(), c.getName(), c.getCode(), c.getPath(),
                c.getLevel(), c.getSort(), c.getEnabled(), c.getDescription(), children);
    }

    private CertificateCategory requireCategory(Long id) {
        CertificateCategory category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BizException(CertErrorCode.CATEGORY_NOT_FOUND);
        }
        return category;
    }
}
