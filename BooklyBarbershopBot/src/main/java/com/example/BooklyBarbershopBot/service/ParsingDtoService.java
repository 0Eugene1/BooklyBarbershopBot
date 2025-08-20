package com.example.BooklyBarbershopBot.service;

import com.example.BooklyBarbershopBot.dto.ServiceDto;
import com.example.BooklyBarbershopBot.dto.StaffDto;
import com.example.BooklyBarbershopBot.service.yclientsService.YclientsService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * Сервис для парсинга DTO объектов (ServiceDto и StaffDto) из сырых JSON-ответов Yclients API.
 * Позволяет преобразовывать JSON-строки в список DTO с учетом разных форматов данных.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ParsingDtoService {

    private final YclientsService yclientsService;

    /**
     * Получить список услуг (ServiceDto) из JSON, возвращаемого Yclients API для указанной компании.
     * Учитывает случаи, когда "data" может быть массивом или одним объектом.
     *
     * @param companyId ID компании в системе Yclients
     * @return список услуг ServiceDto
     * @throws Exception при ошибках парсинга JSON
     */
    public List<ServiceDto> getServicesParsed(String companyId) throws Exception {
        String json = yclientsService.getServicesRawJson(companyId);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);
        JsonNode dataNode = root.path("data");

        if (dataNode.isArray()) {
            return mapper.readValue(dataNode.toString(), new TypeReference<>() {
            });
        } else if (dataNode.isObject()) {
            ServiceDto single = mapper.readValue(dataNode.toString(), ServiceDto.class);
            return List.of(single);
        }
        return Collections.emptyList();
    }

    /**
     * Получить список мастеров (StaffDto) из JSON, возвращаемого Yclients API для указанной компании.
     *
     * @param companyId ID компании в системе Yclients
     * @return список мастеров StaffDto
     * @throws Exception при ошибках парсинга JSON
     */
    public List<StaffDto> getStaffParsed(String companyId) throws Exception {
        String json = yclientsService.getStaffRawJson(companyId);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);
        JsonNode dataNode = root.path("data");
        return mapper.readValue(dataNode.toString(), new TypeReference<>() {
        });
    }
}