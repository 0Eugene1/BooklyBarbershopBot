package com.example.BooklyBarbershopBot.dto;

import lombok.Data;

@Data
public class ApiResponse<T> {
    private boolean success;
    private T data;
    private Object meta;
}
