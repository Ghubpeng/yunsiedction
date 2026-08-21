package com.yunsie.common.web;

/**
 * 请求链路 traceId 持有器（ThreadLocal）。
 * 由 boot 的 TraceIdFilter 设置/清理；Result 自动携带（api-design：traceId 贯穿请求与响应）。
 * 使用 ThreadLocal 而非直接依赖 MDC，保持 common 无日志依赖；
 * TraceIdFilter 会同时写入 MDC 供日志使用。
 */
public final class TraceIdHolder {

    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

    private TraceIdHolder() {
    }

    public static void set(String traceId) {
        HOLDER.set(traceId);
    }

    /** 未设置时返回 null */
    public static String get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
