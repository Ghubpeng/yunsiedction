package com.yunsie.module.user.api;

/**
 * 用户认证信息契约（user 域对外 API）：
 * 供 boot 安全过滤器按请求加载当前用户（服务端重新解析，不信任 token 内的静态信息）。
 */
public interface UserAuthApi {

    /** 按账号（用户名，其次手机号）查询认证视图 */
    UserAuthView findByAccount(String account);

    /** 按用户 ID 查询认证视图 */
    UserAuthView findById(Long userId);

    /**
     * 认证视图（最小必要字段）。
     *
     * @param userId   用户ID
     * @param username 登录账号
     * @param mobile   手机号
     * @param nickname 昵称
     * @param userType 用户类型: 1-学员 2-教师 3-管理员
     * @param status   状态: 1-正常 0-禁用 2-锁定
     */
    record UserAuthView(Long userId, String username, String mobile, String nickname,
                        Integer userType, Integer status) {
    }
}
