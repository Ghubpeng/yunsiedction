package com.yunsie.module.certificate.service;

import com.yunsie.module.certificate.dto.CategoryCreateReq;
import com.yunsie.module.certificate.dto.CategoryMoveReq;
import com.yunsie.module.certificate.dto.CategoryUpdateReq;
import com.yunsie.module.certificate.vo.CategoryVO;

import java.util.List;

/**
 * 证书分类（无限级树）服务。
 * 树模型：parent_id + path(物化路径) + level + sort + enabled。
 */
public interface CertificateCategoryService {

    Long create(CategoryCreateReq req);

    void update(Long id, CategoryUpdateReq req);

    /** 删除保护：存在子分类或分类下存在证书时禁止删除（逻辑删除） */
    void delete(Long id);

    /** 完整树（含禁用节点，后台管理用） */
    List<CategoryVO> tree();

    /** 完整树（仅启用节点，用户公开端用；禁用节点的子树自动隐藏） */
    List<CategoryVO> enabledTree();

    List<CategoryVO> children(Long id);

    /** 移动节点：禁止移动到自身/后代；path 与 level 全子树级联更新 */
    void move(Long id, CategoryMoveReq req);
}
