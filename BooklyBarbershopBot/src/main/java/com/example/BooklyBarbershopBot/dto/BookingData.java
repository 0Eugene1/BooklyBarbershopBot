package com.example.BooklyBarbershopBot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

@Data
@AllArgsConstructor

public class BookingData {

    private String slug;
    private Long serviceId;
    private Long staffId;
    private String datetime; // в формате ISO-8601
    @Getter
    private String staffName;
    @Getter
    private String serviceName;
}
