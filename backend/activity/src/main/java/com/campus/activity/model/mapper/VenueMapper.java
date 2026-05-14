package com.campus.activity.model.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.activity.model.entity.Venue;
import com.campus.activity.model.row.VenueRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface VenueMapper extends BaseMapper<Venue> {
    @Select("""
            SELECT v.venue_id AS id, v.venue_name AS venueName, v.room_number AS roomNumber,
                   v.capacity, v.campus_id AS campusId, c.campus_name AS campusName
            FROM Venue v
            JOIN Campus c ON v.campus_id = c.campus_id
            WHERE (#{campusId} IS NULL OR v.campus_id = #{campusId})
              AND (v.venue_name LIKE #{keyword} OR v.room_number LIKE #{keyword})
            ORDER BY v.venue_id
            """)
    List<VenueRow> listVenues(@Param("campusId") Integer campusId, @Param("keyword") String keyword);

    @Insert("""
            INSERT INTO Venue(venue_name, room_number, capacity, campus_id)
            VALUES (#{venueName}, #{roomNumber}, #{capacity}, #{campusId})
            """)
    int createVenue(@Param("venueName") String venueName,
                    @Param("roomNumber") String roomNumber,
                    @Param("capacity") int capacity,
                    @Param("campusId") int campusId);
}
