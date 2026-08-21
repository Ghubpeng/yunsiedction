package com.yunsie.module.sys.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunsie.common.exception.BizException;
import com.yunsie.module.sys.dto.RoleCreateReq;
import com.yunsie.module.sys.error.SysErrorCode;
import com.yunsie.module.sys.mapper.SysDataScopeMapper;
import com.yunsie.module.sys.mapper.SysPermissionMapper;
import com.yunsie.module.sys.mapper.SysRoleMapper;
import com.yunsie.module.sys.mapper.SysRolePermissionMapper;
import com.yunsie.module.sys.mapper.SysUserRoleMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 角色服务单元测试：编码唯一性（database-design：唯一性由唯一索引+应用层双重保证）。
 */
class SysRoleServiceTest {

    @Test
    void create_duplicateCode_throwsBizException() {
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        when(roleMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        SysRoleServiceImpl service = new SysRoleServiceImpl(
                roleMapper,
                mock(SysPermissionMapper.class),
                mock(SysRolePermissionMapper.class),
                mock(SysDataScopeMapper.class),
                mock(SysUserRoleMapper.class));

        BizException ex = assertThrows(BizException.class,
                () -> service.create(new RoleCreateReq("super_admin", "超级管理员", 2, 1, "", 0)));

        assertEquals(SysErrorCode.ROLE_CODE_EXISTS.code(), ex.getCode());
    }
}
