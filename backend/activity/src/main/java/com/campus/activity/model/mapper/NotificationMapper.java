package com.campus.activity.model.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.activity.model.entity.Notification;
import com.campus.activity.model.row.NotificationRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {
    @Insert("""
            INSERT INTO Notification(recipient_id, type, title, content, related_type, related_id)
            VALUES (#{recipientId}, #{type}, #{title}, #{content}, #{relatedType}, #{relatedId})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "notificationId", keyColumn = "notification_id")
    int insertNotification(Notification notification);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM Notification
            WHERE recipient_id = #{recipientId}
            <if test="unreadOnly">AND is_read = 0</if>
            </script>
            """)
    Long countMine(@Param("recipientId") int recipientId, @Param("unreadOnly") boolean unreadOnly);

    @Select("""
            <script>
            SELECT notification_id AS notificationId, type, title, content,
                   related_type AS relatedType, related_id AS relatedId,
                   is_read AS `read`, created_at AS createdAt
            FROM Notification
            WHERE recipient_id = #{recipientId}
            <if test="unreadOnly">AND is_read = 0</if>
            ORDER BY is_read ASC, created_at DESC, notification_id DESC
            LIMIT #{offset}, #{size}
            </script>
            """)
    List<NotificationRow> listMine(@Param("recipientId") int recipientId,
                                   @Param("unreadOnly") boolean unreadOnly,
                                   @Param("offset") int offset,
                                   @Param("size") int size);

    @Select("""
            SELECT COUNT(*)
            FROM Notification
            WHERE recipient_id = #{recipientId} AND is_read = 0
            """)
    Long countUnread(@Param("recipientId") int recipientId);

    @Update("""
            UPDATE Notification
            SET is_read = 1
            WHERE notification_id = #{notificationId} AND recipient_id = #{recipientId}
            """)
    int markRead(@Param("notificationId") int notificationId, @Param("recipientId") int recipientId);

    @Update("""
            UPDATE Notification
            SET is_read = 1
            WHERE recipient_id = #{recipientId} AND is_read = 0
            """)
    int markAllRead(@Param("recipientId") int recipientId);
}
