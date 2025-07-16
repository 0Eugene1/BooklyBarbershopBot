package com.example.BooklyBarbershopBot.dto;

import lombok.Data;

import java.util.List;

@Data
public class BookTimeResponse {
    private List<BookTimeDto> data;
}
