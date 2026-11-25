package com.shortlink.dto;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;

public record ShortenRequest(
    @NotBlank(message = "URL must not be blank")
    @URL(message = "Must be a valid HTTP/HTTPS URL")
    String originalUrl
) {}
