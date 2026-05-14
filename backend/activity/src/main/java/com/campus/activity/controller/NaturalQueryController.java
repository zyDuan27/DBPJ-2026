package com.campus.activity.controller;

import com.campus.activity.common.Result;
import com.campus.activity.model.dto.NaturalQueryRequest;
import com.campus.activity.model.vo.NaturalQueryResultVO;
import com.campus.activity.service.NaturalQueryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/natural-query")
public class NaturalQueryController {
    private final NaturalQueryService naturalQueryService;

    public NaturalQueryController(NaturalQueryService naturalQueryService) {
        this.naturalQueryService = naturalQueryService;
    }

    @PostMapping
    public Result<NaturalQueryResultVO> query(@Valid @RequestBody NaturalQueryRequest request) {
        return Result.success(naturalQueryService.query(request));
    }
}
