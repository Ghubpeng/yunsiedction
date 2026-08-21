package com.yunsie.common.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Result 统一响应体测试（api-design：code=0 成功）。
 */
class ResultTest {

    @Test
    void ok_setsSuccessCodeAndData() {
        Result<String> result = Result.ok("data");
        assertTrue(result.isSuccess());
        assertEquals(0, result.code());
        assertEquals("data", result.data());
    }

    @Test
    void fail_carriesCodeAndMessage() {
        Result<Void> result = Result.fail(30001, "参数校验失败");
        assertFalse(result.isSuccess());
        assertEquals(30001, result.code());
        assertEquals("参数校验失败", result.message());
        assertNull(result.data());
    }
}
