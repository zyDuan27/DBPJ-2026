package com.campus.activity.service;

import com.campus.activity.common.Access;
import com.campus.activity.common.Role;
import com.campus.activity.model.dto.CampusRequest;
import com.campus.activity.model.dto.CategoryRequest;
import com.campus.activity.model.dto.VenueRequest;
import com.campus.activity.model.mapper.CampusMapper;
import com.campus.activity.model.mapper.CategoryMapper;
import com.campus.activity.model.mapper.VenueMapper;
import com.campus.activity.model.vo.CampusVO;
import com.campus.activity.model.vo.CategoryVO;
import com.campus.activity.model.vo.MutationResultVO;
import com.campus.activity.model.vo.VenueVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class DictionaryService {
    private final CampusMapper campusMapper;
    private final VenueMapper venueMapper;
    private final CategoryMapper categoryMapper;

    public DictionaryService(CampusMapper campusMapper, VenueMapper venueMapper, CategoryMapper categoryMapper) {
        this.campusMapper = campusMapper;
        this.venueMapper = venueMapper;
        this.categoryMapper = categoryMapper;
    }

    public List<CampusVO> campuses() {
        return campusMapper.listCampuses().stream().map(CampusVO::from).toList();
    }

    @Transactional
    public MutationResultVO createCampus(CampusRequest request) {
        Access.require(Role.ADMIN);
        campusMapper.createCampus(request.campusName(), request.location());
        return MutationResultVO.createdResult();
    }

    public List<VenueVO> venues(Integer campusId, String keyword) {
        String normalized = keyword == null ? "" : keyword.trim();
        String like = normalized.isBlank() ? null : "%" + normalized + "%";
        return venueMapper.listVenues(campusId, like).stream().map(VenueVO::from).toList();
    }

    @Transactional
    public MutationResultVO createVenue(VenueRequest request) {
        Access.require(Role.ADMIN);
        venueMapper.createVenue(request.venueName(), request.roomNumber(), request.capacity(), request.campusId());
        return MutationResultVO.createdResult();
    }

    public List<CategoryVO> categories() {
        return categoryMapper.listCategories().stream().map(CategoryVO::from).toList();
    }

    @Transactional
    public MutationResultVO createCategory(CategoryRequest request) {
        Access.require(Role.ADMIN);
        categoryMapper.createCategory(request.categoryName());
        return MutationResultVO.createdResult();
    }
}
