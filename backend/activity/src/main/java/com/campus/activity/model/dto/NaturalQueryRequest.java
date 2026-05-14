package com.campus.activity.model.dto;

import jakarta.validation.constraints.NotBlank;

public record NaturalQueryRequest(@NotBlank String question,
                                  Integer page,
                                  Integer size) {
}
