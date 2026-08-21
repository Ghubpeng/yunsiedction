package com.yunsie.common.error;

/**
 * 错误码码段约定（api-design，强制）。
 *
 * <pre>
 * 1xxxx  认证      10001 未登录、10002 token 过期
 * 2xxxx  权限      20001 无操作权限、20002 越权访问他人资源
 * 3xxxx  业务      30001 余额不足、30002 考试已交卷、30003 题目不存在
 * 4xxxx  第三方    40001 AI 服务异常、40002 支付渠道异常
 * 5xxxx  系统      50001 系统繁忙
 * </pre>
 *
 * 各业务域在对应码段内自行分段，避免冲突。
 */
public final class ErrorCodeRange {

    public static final int AUTH_MIN = 10000;
    public static final int AUTH_MAX = 19999;

    public static final int PERMISSION_MIN = 20000;
    public static final int PERMISSION_MAX = 29999;

    public static final int BUSINESS_MIN = 30000;
    public static final int BUSINESS_MAX = 39999;

    public static final int THIRD_PARTY_MIN = 40000;
    public static final int THIRD_PARTY_MAX = 49999;

    public static final int SYSTEM_MIN = 50000;
    public static final int SYSTEM_MAX = 59999;

    private ErrorCodeRange() {
    }
}
