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
 * Сервис-десериализатор для преобразования сырых JSON-ответов Yclients в типизированные DTO.
 * <p>
 * Основная задача — извлечение полезной нагрузки из узла "data" и её маппинг
 * в списки объектов {@link ServiceDto} и {@link StaffDto}.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ParsingDtoService {

    private final YclientsService yclientsService;
    private final ObjectMapper mapper = new ObjectMapper(); // Инжектируйте или создайте один экземпляр

    /**
     * Парсит список услуг для конкретного филиала.
     * <p>
     * Логика поддерживает как массовый возврат услуг (массив), так и
     * единичную услугу (объект), оборачивая её в список для консистентности.
     *
     * @param companyId идентификатор филиала Yclients.
     * @return список услуг.
     * @throws Exception при ошибках структуры JSON или несовпадении полей DTO.
     */
    public List<ServiceDto> getServicesParsed(String companyId) throws Exception {
        String json = yclientsService.getServicesRawJson(companyId);
        JsonNode root = mapper.readTree(json);
        JsonNode dataNode = root.path("data");

        if (dataNode.isArray()) {
            return mapper.readValue(dataNode.toString(), new TypeReference<List<ServiceDto>>() {});
        } else if (dataNode.isObject()) {
            ServiceDto single = mapper.readValue(dataNode.toString(), ServiceDto.class);
            return List.of(single);
        }

        log.warn("Узел 'data' в ответе услуг для компании {} пуст или некорректен", companyId);
        return Collections.emptyList();
    }

    /**
     * Парсит список сотрудников (мастеров) для филиала.
     *
     * @param companyId идентификатор филиала Yclients.
     * @return список мастеров.
     * @throws Exception при ошибках десериализации.
     */
    public List<StaffDto> getStaffParsed(String companyId) throws Exception {
        String json = yclientsService.getStaffRawJson(companyId);
        JsonNode root = mapper.readTree(json);
        JsonNode dataNode = root.path("data");

        if (!dataNode.isArray()) {
            log.warn("Ожидался массив мастеров для компании {}, но получено: {}", companyId, dataNode.getNodeType());
            return Collections.emptyList();
        }

        return mapper.readValue(dataNode.toString(), new TypeReference<List<StaffDto>>() {});
    }
}