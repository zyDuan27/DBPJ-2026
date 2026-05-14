package com.campus.activity.model.mapper;

import com.campus.activity.model.row.CampusUsageRow;
import com.campus.activity.model.row.CategoryPopularityRow;
import com.campus.activity.model.row.StatsOverviewRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface StatsMapper {
    @Select("""
            SELECT
              (SELECT COUNT(*) FROM Activity) AS activityCount,
              (SELECT COUNT(*) FROM Activity WHERE status = 'PENDING_REVIEW') AS pendingReviewCount,
              (SELECT COUNT(*) FROM Activity WHERE status = 'PUBLISHED') AS publishedCount,
              (SELECT COUNT(*) FROM Registration WHERE status IN ('ENROLLED', 'WAITLISTED', 'CHECKED_IN')) AS registrationCount,
              (SELECT COUNT(*) FROM Registration WHERE status = 'CHECKED_IN') AS checkedInCount
            """)
    StatsOverviewRow overview();

    @Select("""
            SELECT c.campus_id AS campusId, c.campus_name AS campusName,
                   COUNT(DISTINCT a.activity_id) AS activityCount,
                   COUNT(DISTINCT v.venue_id) AS venueCount
            FROM Campus c
            LEFT JOIN Venue v ON c.campus_id = v.campus_id
            LEFT JOIN Activity a ON v.venue_id = a.venue_id
            GROUP BY c.campus_id, c.campus_name
            ORDER BY c.campus_id
            """)
    List<CampusUsageRow> campusUsage();

    @Select("""
            SELECT cat.category_id AS categoryId, cat.category_name AS categoryName,
                   COUNT(a.activity_id) AS activityCount,
                   COALESCE(AVG(a.current_enrollment), 0) AS averageEnrollment
            FROM Category cat
            LEFT JOIN Activity a ON cat.category_id = a.category_id
            GROUP BY cat.category_id, cat.category_name
            ORDER BY activityCount DESC
            """)
    List<CategoryPopularityRow> categoryPopularity();
}
