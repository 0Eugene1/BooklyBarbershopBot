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
 * Сервис-посредник для работы с ресурсными данными Yclients.
 * <p>
 * Отвечает за получение, парсинг и кэширование справочной информации:
 * списков сотрудников и каталога услуг. Позволяет быстро сопоставлять ID
 * объектов с их понятными названиями для отображения пользователю.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class YclientsDataService {

    private final YclientsHttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Локальный кэш имен мастеров для мгновенного доступа (Staff ID -> Name) */
    private final Map<Long, String> staffNamesCache = new ConcurrentHashMap<>();

    /** Локальный кэш названий услуг для мгновенного доступа (Service ID -> Title) */
    private final Map<Long, String> serviceNamesCache = new ConcurrentHashMap<>();

    /**
     * Запрашивает список мастеров и обновляет локальный кэш имен.
     *
     * @param companyId ID филиала в системе Yclients.
     * @return список объектов {@link StaffDto} или пустой список при сбое.
     */
    public List<StaffDto> getStaffList(String companyId) {
        try {
            String raw = httpClient.getStaffRaw(companyId);
            JsonNode root = objectMapper.readTree(raw);
            JsonNode dataNode = root.get("data");
            if (dataNode == null || !dataNode.isArray()) {
                log.warn("API Yclients вернуло пустой список или некорректный формат данных мастеров");
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
            log.error("Критическая ошибка парсинга мастеров для компании {}: {}", companyId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Запрашивает "свежие" данные мастеров без записи в основной кэш имен.
     * Полезно для задач синхронизации, где важна актуальность всех полей объекта.
     */
    public List<StaffDto> getStaffListFresh(String companyId) {
        try {
            String raw = httpClient.getStaffRaw(companyId);
            JsonNode root = objectMapper.readTree(raw);
            JsonNode dataNode = root.get("data");
            if (dataNode == null || !dataNode.isArray()) return Collections.emptyList();

            List<StaffDto> result = new ArrayList<>();
            for (JsonNode node : dataNode) {
                result.add(objectMapper.treeToValue(node, StaffDto.class));
            }
            return result;
        } catch (Exception e) {
            log.error("Ошибка получения актуальных данных мастеров: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Получает каталог услуг филиала и кэширует их названия.
     *
     * @param companyId ID филиала.
     * @return список доступных услуг {@link ServiceDto}.
     */
    public List<ServiceDto> getServiceList(String companyId) {
        try {
            String raw = httpClient.getServicesRaw(companyId);
            JsonNode root = objectMapper.readTree(raw);
            JsonNode dataNode = root.get("data");
            if (dataNode == null || !dataNode.isArray()) return Collections.emptyList();

            List<ServiceDto> result = new ArrayList<>();
            for (JsonNode node : dataNode) {
                ServiceDto service = objectMapper.treeToValue(node, ServiceDto.class);
                result.add(service);
                serviceNamesCache.put(service.getId(), service.getTitle());
            }
            return result;
        } catch (Exception e) {
            log.error("Ошибка парсинга услуг Yclients: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Возвращает имя мастера. Если данных нет в кэше, инициирует обновление.
     */
    public String getStaffName(String companyId, Long staffId) {
        if (staffNamesCache.containsKey(staffId)) {
            return staffNamesCache.get(staffId);
        }
        getStaffList(companyId);
        return staffNamesCache.getOrDefault(staffId, "Неизвестный мастер");
    }

    /**
     * Возвращает название услуги. Если данных нет в кэше, инициирует обновление.
     */
    public String getServiceName(String companyId, Long serviceId) {
        if (serviceNamesCache.containsKey(serviceId)) {
            return serviceNamesCache.get(serviceId);
        }
        getServiceList(companyId);
        return serviceNamesCache.getOrDefault(serviceId, "Неизвестная услуга");
    }
}