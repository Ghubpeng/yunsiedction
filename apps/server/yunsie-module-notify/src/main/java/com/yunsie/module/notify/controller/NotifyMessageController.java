package com.yunsie.module.notify.controller;

import com.yunsie.common.api.PageResult;
import com.yunsie.common.api.Result;
import com.yunsie.common.security.CurrentUser;
import com.yunsie.module.notify.service.NotifyMessageService;
import com.yunsie.module.notify.vo.MessageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站内消息接口（登录即可；严格 @CurrentUser 本人数据，禁止 userId 入参）。
 */
@RestController
@RequestMapping("/api/v1/notify/messages")
@RequiredArgsConstructor
@Validated
public class NotifyMessageController {

    private final NotifyMessageService messageService;

    @GetMapping
    public Result<PageResult<MessageVO>> page(@CurrentUser Long userId,
                                              @RequestParam(defaultValue = "1") Integer pageNum,
                                              @RequestParam(defaultValue = "10") Integer pageSize,
                                              @RequestParam(required = false) Boolean unreadOnly) {
        return Result.ok(messageService.page(userId, pageNum, pageSize, unreadOnly));
    }

    @GetMapping("/unread-count")
    public Result<Long> unreadCount(@CurrentUser Long userId) {
        return Result.ok(messageService.unreadCount(userId));
    }

    @PutMapping("/{id}/read")
    public Result<Void> markRead(@CurrentUser Long userId, @PathVariable Long id) {
        messageService.markRead(userId, id);
        return Result.ok(null);
    }

    @PutMapping("/read-all")
    public Result<Void> markAllRead(@CurrentUser Long userId) {
        messageService.markAllRead(userId);
        return Result.ok(null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@CurrentUser Long userId, @PathVariable Long id) {
        messageService.delete(userId, id);
        return Result.ok(null);
    }
}
