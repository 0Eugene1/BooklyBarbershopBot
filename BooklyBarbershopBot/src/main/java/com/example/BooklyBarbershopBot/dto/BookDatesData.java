package com.example.BooklyBarbershopBot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class BookDatesData {

    @JsonProperty("booking_days")
    private Map<String, List<String>> bookingDays;

    @JsonProperty("booking_dates")
    private List<String> bookingDates;

    @JsonProperty("working_days")
    private Map<String, List<String>> workingDays;

    @JsonProperty("working_dates")
    private List<String> workingDates;
}
