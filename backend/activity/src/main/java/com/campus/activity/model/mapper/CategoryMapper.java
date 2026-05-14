package com.campus.activity.model.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.activity.model.entity.Category;
import com.campus.activity.model.row.CategoryRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CategoryMapper extends BaseMapper<Category> {
    @Select("""
            SELECT category_id AS id, category_name AS categoryName
            FROM Category
            ORDER BY category_id
            """)
    List<CategoryRow> listCategories();

    @Insert("INSERT INTO Category(category_name) VALUES (#{categoryName})")
    int createCategory(@Param("categoryName") String categoryName);
}
