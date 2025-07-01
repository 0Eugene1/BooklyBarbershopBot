package com.example.BooklyBarbershopBot.service;

import com.example.BooklyBarbershopBot.dto.ServiceDto;
import com.example.BooklyBarbershopBot.dto.StaffDto;
import com.example.BooklyBarbershopBot.service.yclients.YclientsService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class ParsingDtoService {

    private final YclientsService yclientsService;

    public ParsingDtoService(YclientsService yclientsService) {
        this.yclientsService = yclientsService;
    }

    public List<ServiceDto> getServicesParsed(String companyId) throws Exception {
        String json = yclientsService.getServicesRawJson(companyId); // ✅ исправлено
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);
        JsonNode dataNode = root.path("data");
        return mapper.readValue(dataNode.toString(), new TypeReference<List<ServiceDto>>() {});
    }

    public List<StaffDto> getStaffParsed(String companyId) throws Exception {
        String json = yclientsService.getStaffRawJson(companyId); // ✅ исправлено
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);
        JsonNode dataNode = root.path("data");
        return mapper.readValue(dataNode.toString(), new TypeReference<List<StaffDto>>() {});
    }
}



