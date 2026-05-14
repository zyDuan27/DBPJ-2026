package com.campus.activity.model.vo;

import com.campus.activity.model.row.NotificationRow;

import java.time.LocalDateTime;

public record NotificationVO(Integer notificationId,
                             String type,
                             String title,
                             String content,
                             String relatedType,
                             Integer relatedId,
                             Boolean read,
                             LocalDateTime createdAt) {
    public static NotificationVO from(NotificationRow row) {
        return new NotificationVO(
                row.getNotificationId(),
                row.getType(),
                row.getTitle(),
                row.getContent(),
                row.getRelatedType(),
                row.getRelatedId(),
                row.getRead(),
                row.getCreatedAt()
        );
    }
}
