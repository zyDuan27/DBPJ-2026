package com.campus.activity.service;

import com.campus.activity.common.AuthContext;
import com.campus.activity.common.BusinessException;
import com.campus.activity.common.CurrentUser;
import com.campus.activity.common.PageResult;
import com.campus.activity.model.entity.Notification;
import com.campus.activity.model.mapper.NotificationMapper;
import com.campus.activity.model.vo.NotificationVO;
import com.campus.activity.model.vo.UnreadCountVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class NotificationService {
    private final NotificationMapper notificationMapper;

    public NotificationService(NotificationMapper notificationMapper) {
        this.notificationMapper = notificationMapper;
    }

    public PageResult<NotificationVO> my(int page, int size, boolean unreadOnly) {
        CurrentUser user = AuthContext.get();
        long total = notificationMapper.countMine(user.id(), unreadOnly);
        List<NotificationVO> rows = notificationMapper.listMine(user.id(), unreadOnly, (page - 1) * size, size)
                .stream()
                .map(NotificationVO::from)
                .toList();
        return new PageResult<>(rows, total, page, size);
    }

    public UnreadCountVO unreadCount() {
        CurrentUser user = AuthContext.get();
        return new UnreadCountVO(notificationMapper.countUnread(user.id()));
    }

    @Transactional
    public void markRead(int notificationId) {
        CurrentUser user = AuthContext.get();
        if (notificationMapper.markRead(notificationId, user.id()) == 0) {
            throw new BusinessException(40401, "通知不存在");
        }
    }

    @Transactional
    public int markAllRead() {
        CurrentUser user = AuthContext.get();
        return notificationMapper.markAllRead(user.id());
    }

    @Transactional
    public void create(int recipientId, String type, String title, String content,
                       String relatedType, Integer relatedId) {
        Notification notification = new Notification();
        notification.setRecipientId(recipientId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setRelatedType(relatedType);
        notification.setRelatedId(relatedId);
        notificationMapper.insertNotification(notification);
    }

    @Transactional
    public void createMany(List<Integer> recipientIds, String type, String title, String content,
                           String relatedType, Integer relatedId) {
        recipientIds.stream()
                .distinct()
                .forEach(recipientId -> create(recipientId, type, title, content, relatedType, relatedId));
    }
}
