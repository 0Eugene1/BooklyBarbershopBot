package com.example.BooklyBarbershopBot.dto;

import com.example.BooklyBarbershopBot.deserializer.ImageGroupDeserializer;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ServiceDto {
    private Long id;

    @JsonProperty("booking_title")
    private String bookingTitle;

    @JsonProperty("salon_service_id")
    private Long salonServiceId;

    @JsonProperty("category_id")
    private Long categoryId;

    private String title;

    @JsonProperty("price_min")
    private Double priceMin;

    @JsonProperty("price_max")
    private Double priceMax;

    private Integer active;

    private String comment;

    @JsonProperty("api_id")
    private String apiId;

    private Integer weight;

    private List<StaffDto> staff;

    @JsonDeserialize(using = ImageGroupDeserializer.class)
    @JsonProperty("image_group")
    private List<ImageGroupDto> imageGroup;
}


