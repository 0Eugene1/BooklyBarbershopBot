package com.example.BooklyBarbershopBot.dto;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BookingData {

    private String slug;
    private Long serviceId;
    private Long staffId;
    private String datetime; // в формате ISO-8601
    @Getter
    private String staffName;
    @Getter
    private String serviceName;
    private String phone;
    private boolean awaitingCode;

    //Поля для удаление записи
    private Long recordId;
    private String recordHash;

    @Getter
    @Setter
    private String fullName;           // новое поле — имя клиента
    @Getter
    @Setter
    private boolean awaitingFullName;  // новый флаг — ожидаем имя клиента
}
