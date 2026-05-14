package com.campus.activity.model.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.activity.model.entity.Campus;
import com.campus.activity.model.row.CampusRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CampusMapper extends BaseMapper<Campus> {
    @Select("""
            SELECT campus_id AS id, campus_name AS campusName, location
            FROM Campus
            ORDER BY campus_id
            """)
    List<CampusRow> listCampuses();

    @Insert("INSERT INTO Campus(campus_name, location) VALUES (#{campusName}, #{location})")
    int createCampus(@Param("campusName") String campusName, @Param("location") String location);
}
