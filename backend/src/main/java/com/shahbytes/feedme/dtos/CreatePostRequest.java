package com.shahbytes.feedme.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePostRequest(
        @NotBlank String idempotencyKey,
        @NotBlank String author_id,
        @NotBlank @Size(max = 200) String content
) {
}
