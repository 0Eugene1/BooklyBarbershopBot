package com.example.BooklyBarbershopBot.service.yclientsService;

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

/**
 * Сервис для получения данных мастеров и услуг из Yclients через HTTP-клиент.
 * Поддерживает кэширование имен мастеров и услуг для оптимизации повторных запросов.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class YclientsDataService {

    private final YclientsHttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    // Кэш имен мастеров: ключ — ID мастера, значение — имя
    private final Map<Long, String> staffNamesCache = new ConcurrentHashMap<>();
    // Кэш названий услуг: ключ — ID услуги, значение — название
    private final Map<Long, String> serviceNamesCache = new ConcurrentHashMap<>();

    /**
     * Получить список мастеров (StaffDto) из Yclients для указанной компании.
     * Результат кэшируется в staffNamesCache.
     *
     * @param companyId идентификатор компании Yclients
     * @return список мастеров или пустой список при ошибке
     */
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

    /**
     * Получить список мастеров без обновления кэша имен.
     * Используется, когда нужны актуальные данные прямо из API,
     * но мы не хотим перезаписывать существующий кэш.
     */
    public List<StaffDto> getStaffListFresh(String companyId) {
        try {
            String raw = httpClient.getStaffRaw(companyId);
            log.info("Raw staff data from Yclients: {}", raw);
            JsonNode root = objectMapper.readTree(raw);
            JsonNode dataNode = root.get("data");
            if (dataNode == null || !dataNode.isArray()) {
                log.warn("Нет данных мастеров в ответе Yclients (fresh)");
                return Collections.emptyList();

            }

            List<StaffDto> result = new ArrayList<>();
            for (JsonNode node : dataNode) {
                StaffDto staff = objectMapper.treeToValue(node, StaffDto.class);
                result.add(staff);
            }
            return result;
        } catch (Exception e) {
            log.error("Ошибка при парсинге мастеров из Yclients (fresh)", e);
            return Collections.emptyList();
        }
    }


    /**
     * Получить список услуг (ServiceDto) из Yclients для указанной компании.
     * Результат кэшируется в serviceNamesCache.
     *
     * @param companyId идентификатор компании Yclients
     * @return список услуг или пустой список при ошибке
     */
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

    /**
     * Получить имя мастера по ID, используя кэш или обновляя его при необходимости.
     *
     * @param companyId идентификатор компании Yclients
     * @param staffId   ID мастера
     * @return имя мастера или "Неизвестный мастер" если не найден
     */
    public String getStaffName(String companyId, Long staffId) {
        // Сначала из кеша
        if (staffNamesCache.containsKey(staffId)) {
            return staffNamesCache.get(staffId);
        }
        // Иначе обновляем кеш
        getStaffList(companyId);
        return staffNamesCache.getOrDefault(staffId, "Неизвестный мастер");
    }

    /**
     * Получить название услуги по ID, используя кэш или обновляя его при необходимости.
     *
     * @param companyId идентификатор компании Yclients
     * @param serviceId ID услуги
     * @return название услуги или "Неизвестная услуга" если не найдена
     */
    public String getServiceName(String companyId, Long serviceId) {
        if (serviceNamesCache.containsKey(serviceId)) {
            return serviceNamesCache.get(serviceId);
        }
        getServiceList(companyId);
        return serviceNamesCache.getOrDefault(serviceId, "Неизвестная услуга");
    }
}