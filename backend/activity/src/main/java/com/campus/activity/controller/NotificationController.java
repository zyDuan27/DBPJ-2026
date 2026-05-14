package com.campus.activity.controller;

import com.campus.activity.common.PageResult;
import com.campus.activity.common.Result;
import com.campus.activity.model.vo.NotificationVO;
import com.campus.activity.model.vo.UnreadCountVO;
import com.campus.activity.service.NotificationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public Result<PageResult<NotificationVO>> my(@RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "10") int size,
                                                 @RequestParam(defaultValue = "false") boolean unreadOnly) {
        return Result.success(notificationService.my(page, size, unreadOnly));
    }

    @GetMapping("/unread-count")
    public Result<UnreadCountVO> unreadCount() {
        return Result.success(notificationService.unreadCount());
    }

    @PatchMapping("/{notificationId}/read")
    public Result<Void> markRead(@PathVariable int notificationId) {
        notificationService.markRead(notificationId);
        return Result.success();
    }

    @PatchMapping("/read-all")
    public Result<Integer> markAllRead() {
        return Result.success(notificationService.markAllRead());
    }
}
