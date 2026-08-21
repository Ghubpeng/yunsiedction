package com.yunsie.module.user.service;

import com.yunsie.common.api.PageResult;
import com.yunsie.module.user.dto.UserCreateReq;
import com.yunsie.module.user.dto.UserUpdateReq;
import com.yunsie.module.user.vo.UserVO;

import java.util.List;

/**
 * 用户账号管理（user 域内部服务）。
 * 数据范围（DataScope）：所有读取/修改他人数据的方法都要求
 * 「目标用户 = 当前用户」或「当前用户拥有全部数据范围」，否则拒绝（permission-rbac）。
 */
public interface UserService {

    /** 创建用户（含凭证哈希与可选角色分配，事务）；调用方须有 sys:user:create 权限 */
    Long create(UserCreateReq req);

    void update(Long currentUserId, Long id, UserUpdateReq req);

    /** 状态变更：1-正常 0-禁用 2-锁定 */
    void updateStatus(Long currentUserId, Long id, int status);

    /** 管理员重置密码（BCrypt 哈希入库，不明文） */
    void resetPassword(Long currentUserId, Long id, String newPassword);

    /** 覆盖式分配角色（经 sys 域契约 SysUserRoleApi） */
    void assignRoles(Long currentUserId, Long id, List<Long> roleIds);

    /** 逻辑删除：账号 + 资料 + 凭证 + 刷新令牌 + 角色关联 */
    void delete(Long currentUserId, Long id);

    UserVO get(Long currentUserId, Long id);

    /** 数据范围：全部数据 → 全量列表；否则仅本人记录 */
    PageResult<UserVO> page(Long currentUserId, int pageNum, int pageSize, String keyword, Integer status);
}
