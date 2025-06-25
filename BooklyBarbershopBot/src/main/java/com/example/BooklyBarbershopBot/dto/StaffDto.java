package com.example.BooklyBarbershopBot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class StaffDto {
    private Long id;

    @JsonProperty("seance_length")
    private Integer seanceLength; // в секундах

    private Integer technological_card_id;
    private String name;
    private String specialization;
    private List<PositionDto> position;
    private Boolean bookable;
    private Integer weight;
    private Integer show_rating;
    private Double rating;
    private Integer votes_count;
    private Integer comments_count;
    private String avatar;
    private String information;
    private String seance_date;
}