package com.sulama.model.dto;

import lombok.Data;

@Data
public class LoginResponse {
    private final String token;
    private final String type = "Bearer";
}
