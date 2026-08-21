package com.yunsie.module.certificate.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunsie.common.exception.BizException;
import com.yunsie.module.certificate.dto.CategoryCreateReq;
import com.yunsie.module.certificate.dto.CategoryMoveReq;
import com.yunsie.module.certificate.entity.Certificate;
import com.yunsie.module.certificate.entity.CertificateCategory;
import com.yunsie.module.certificate.error.CertErrorCode;
import com.yunsie.module.certificate.mapper.CertificateCategoryMapper;
import com.yunsie.module.certificate.mapper.CertificateMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 证书分类服务单元测试：编码唯一 / path·level 计算 / 移动保护 / 删除保护。
 */
class CertificateCategoryServiceTest {

    private CertificateCategoryServiceImpl service(CertificateCategoryMapper categoryMapper,
                                                   CertificateMapper certificateMapper) {
        return new CertificateCategoryServiceImpl(categoryMapper, certificateMapper);
    }

    private CertificateCategory category(Long id, Long parentId, String path, int level) {
        CertificateCategory c = new CertificateCategory();
        c.setId(id);
        c.setParentId(parentId);
        c.setPath(path);
        c.setLevel(level);
        return c;
    }

    @Test
    void create_duplicateCode_throws() {
        CertificateCategoryMapper categoryMapper = mock(CertificateCategoryMapper.class);
        when(categoryMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        BizException ex = assertThrows(BizException.class,
                () -> service(categoryMapper, mock(CertificateMapper.class))
                        .create(new CategoryCreateReq(0L, "医护", "medical", 0, 1, "")));
        assertEquals(CertErrorCode.CATEGORY_CODE_EXISTS.code(), ex.getCode());
    }

    @Test
    void create_root_computesPathAndLevel() {
        CertificateCategoryMapper categoryMapper = mock(CertificateCategoryMapper.class);
        when(categoryMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        doAnswer(inv -> {
            CertificateCategory c = inv.getArgument(0);
            c.setId(10L);
            return 1;
        }).when(categoryMapper).insert(any(CertificateCategory.class));

        Long id = service(categoryMapper, mock(CertificateMapper.class))
                .create(new CategoryCreateReq(0L, "医护", "medical", 0, 1, ""));

        assertEquals(10L, id);
        // 回填物化路径 /10/ 且 level=1
        ArgumentCaptor<CertificateCategory> captor = ArgumentCaptor.forClass(CertificateCategory.class);
        verify(categoryMapper).updateById(captor.capture());
        assertEquals("/10/", captor.getValue().getPath());
        assertEquals(1, captor.getValue().getLevel());
    }

    @Test
    void move_toDescendant_blocked() {
        CertificateCategoryMapper categoryMapper = mock(CertificateCategoryMapper.class);
        CertificateCategory node = category(2L, 1L, "/1/2/", 2);
        CertificateCategory newParent = category(3L, 2L, "/1/2/3/", 3);
        when(categoryMapper.selectById(2L)).thenReturn(node);
        when(categoryMapper.selectById(3L)).thenReturn(newParent);

        BizException ex = assertThrows(BizException.class,
                () -> service(categoryMapper, mock(CertificateMapper.class))
                        .move(2L, new CategoryMoveReq(3L, 0)));
        assertEquals(CertErrorCode.CATEGORY_MOVE_INVALID.code(), ex.getCode());
    }

    @Test
    void move_toSelf_blocked() {
        CertificateCategoryMapper categoryMapper = mock(CertificateCategoryMapper.class);
        when(categoryMapper.selectById(2L)).thenReturn(category(2L, 1L, "/1/2/", 2));

        BizException ex = assertThrows(BizException.class,
                () -> service(categoryMapper, mock(CertificateMapper.class))
                        .move(2L, new CategoryMoveReq(2L, 0)));
        assertEquals(CertErrorCode.CATEGORY_MOVE_INVALID.code(), ex.getCode());
    }

    @Test
    void delete_withChildren_blocked() {
        CertificateCategoryMapper categoryMapper = mock(CertificateCategoryMapper.class);
        when(categoryMapper.selectById(1L)).thenReturn(category(1L, 0L, "/1/", 1));
        when(categoryMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        BizException ex = assertThrows(BizException.class,
                () -> service(categoryMapper, mock(CertificateMapper.class)).delete(1L));
        assertEquals(CertErrorCode.CATEGORY_HAS_CHILDREN.code(), ex.getCode());
    }
}
