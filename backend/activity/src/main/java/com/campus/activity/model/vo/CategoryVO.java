package com.campus.activity.model.vo;

import com.campus.activity.model.row.CategoryRow;

public record CategoryVO(Integer id, String categoryName) {
    public static CategoryVO from(CategoryRow row) {
        return new CategoryVO(
                row.getId(),
                row.getCategoryName()
        );
    }
}
