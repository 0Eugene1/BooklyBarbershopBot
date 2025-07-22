package com.example.BooklyBarbershopBot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

//FIXME WHY NEVER USED?
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CategoryDto {

    private Long id;
    private String title;
}
