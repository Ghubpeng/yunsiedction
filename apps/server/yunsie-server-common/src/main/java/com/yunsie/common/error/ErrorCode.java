package com.yunsie.common.error;

/**
 * 业务错误码接口（api-design：错误码集中定义，禁止魔法数字散落）。
 * 各业务域在本域内定义枚举实现本接口，码段分配见 {@link ErrorCodeRange}。
 */
public interface ErrorCode {

    /** 错误码 */
    int code();

    /** 错误描述（用户可读） */
    String message();
}
