package com.example.BooklyBarbershopBot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
//Хранение recordId и recordHash
public class RecordInfo {

    private Long recordId;
    private String recordHash;
}
