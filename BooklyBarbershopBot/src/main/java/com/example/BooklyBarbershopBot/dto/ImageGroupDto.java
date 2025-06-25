package com.example.BooklyBarbershopBot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class ImageGroupDto {
    // в зависимости от структуры можно добавить поля, либо оставить пустым,
    // если пока не нужен

    private Long id;
}
