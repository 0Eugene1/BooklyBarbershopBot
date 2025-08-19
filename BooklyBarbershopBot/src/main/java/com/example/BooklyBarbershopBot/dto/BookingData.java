package com.example.BooklyBarbershopBot.dto;

import lombok.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BookingData {

    private String slug;
    //private Long serviceId;
    private Long staffId;
    private OffsetDateTime datetime; // в формате ISO-8601
    @Getter
    private String staffName;
    @Getter
    //FIXME TEST 11.08
    private List<String> serviceNames = new ArrayList<>();
    //private String serviceName;
    private String phone;
    private boolean awaitingCode;
    //Поля для удаление записи
    private Long recordId;
    private String recordHash;
    @Getter
    @Setter
    private String fullName;// новое поле — имя клиента
    @Getter
    @Setter
    private boolean awaitingFullName;  // новый флаг — ожидаем имя клиента
    private List<Long> serviceIds = new ArrayList<>();
}
