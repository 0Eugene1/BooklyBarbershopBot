package com.example.BooklyBarbershopBot.service.yclients;

import com.example.BooklyBarbershopBot.dto.ServiceDto;
import com.example.BooklyBarbershopBot.dto.StaffDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class YclientsDataService {

    private final YclientsHttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<Long, String> staffNamesCache = new ConcurrentHashMap<>();
    private final Map<Long, String> serviceNamesCache = new ConcurrentHashMap<>();

    // Получить список мастеров как DTO
    public List<StaffDto> getStaffList(String companyId) {
        try {
            String raw = httpClient.getStaffRaw(companyId);
            JsonNode root = objectMapper.readTree(raw);
            JsonNode dataNode = root.get("data");
            if (dataNode == null || !dataNode.isArray()) {
                log.warn("Нет данных мастеров в ответе Yclients");
                return Collections.emptyList();
            }

            List<StaffDto> result = new ArrayList<>();
            for (JsonNode node : dataNode) {
                StaffDto staff = objectMapper.treeToValue(node, StaffDto.class);
                result.add(staff);
                staffNamesCache.put(staff.getId(), staff.getName());
            }
            return result;
        } catch (Exception e) {
            log.error("Ошибка при парсинге мастеров из Yclients", e);
            return Collections.emptyList();
        }
    }

    // Получить список услуг как DTO
    public List<ServiceDto> getServiceList(String companyId) {
        try {
            String raw = httpClient.getServicesRaw(companyId);
            JsonNode root = objectMapper.readTree(raw);
            JsonNode dataNode = root.get("data");
            if (dataNode == null || !dataNode.isArray()) {
                log.warn("Нет данных услуг в ответе Yclients");
                return Collections.emptyList();
            }

            List<ServiceDto> result = new ArrayList<>();
            for (JsonNode node : dataNode) {
                ServiceDto service = objectMapper.treeToValue(node, ServiceDto.class);
                result.add(service);
                serviceNamesCache.put(service.getId(), service.getTitle());
            }
            return result;
        } catch (Exception e) {
            log.error("Ошибка при парсинге услуг из Yclients", e);
            return Collections.emptyList();
        }
    }

    public String getStaffName(String companyId, Long staffId) {
        // Сначала из кеша
        if (staffNamesCache.containsKey(staffId)) {
            return staffNamesCache.get(staffId);
        }
        // Иначе обновляем кеш
        getStaffList(companyId);
        return staffNamesCache.getOrDefault(staffId, "Неизвестный мастер");
    }

    public String getServiceName(String companyId, Long serviceId) {
        if (serviceNamesCache.containsKey(serviceId)) {
            return serviceNamesCache.get(serviceId);
        }
        getServiceList(companyId);
        return serviceNamesCache.getOrDefault(serviceId, "Неизвестная услуга");
    }
}
