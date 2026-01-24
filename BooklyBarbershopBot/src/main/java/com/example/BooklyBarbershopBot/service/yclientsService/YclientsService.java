package com.example.BooklyBarbershopBot.service.yclientsService;

import com.example.BooklyBarbershopBot.dto.*;
import com.example.BooklyBarbershopBot.entity.Barbershop;
import com.example.BooklyBarbershopBot.entity.Client;
import com.example.BooklyBarbershopBot.globalException.YclientsSmsConfirmationException;
import com.example.BooklyBarbershopBot.service.BarbershopService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Высокоуровневый сервис для управления бизнес-логикой взаимодействия с Yclients.
 * <p>
 * Класс координирует работу HTTP-клиента, сервиса данных и локальных настроек
 * барбершопов для выполнения операций записи, отмены и получения расписания.
 */
@SuppressWarnings("ALL")
@Service
@RequiredArgsConstructor
@Slf4j
public class YclientsService {

    private final YclientsHttpClient httpClient;
    private final YclientsDataService dataService;
    private final BarbershopService barbershopService;

    @Value("${yclients.partner-token}")
    private String partnerToken;

    @Value("${yclients.user-token}")
    private String userToken;

    /**
     * Получить сырые JSON данные мастеров (необработанные).
     * Используется для отладки или получения расширенных полей, не вошедших в DTO.
     *
     * @param companyId ID компании
     * @return JSON строка с данными мастеров
     */
    public String getStaffRawJson(String companyId) {
        return httpClient.getStaffRaw(companyId);
    }

    /**
     * Получить сырые JSON данные услуг (необработанные).
     * Позволяет увидеть полную структуру категорий и параметров услуг Yclients.
     *
     * @param companyId ID компании
     * @return JSON строка с данными услуг
     */
    public String getServicesRawJson(String companyId) {
        return httpClient.getServicesRaw(companyId);
    }

    /**
     * Возвращает список мастеров филиала (с использованием кэша имен).
     */
    public List<StaffDto> getStaff(String companyId) {
        return dataService.getStaffList(companyId);
    }

    /**
     * Возвращает актуальный список мастеров без записи в основной кэш.
     */
    public List<StaffDto> getFreshStaff(String companyId) {
        return dataService.getStaffListFresh(companyId);
    }

    /**
     * Возвращает каталог услуг филиала.
     */
    public List<ServiceDto> getServices(String companyId) {
        return dataService.getServiceList(companyId);
    }

    /**
     * Возвращает имя мастера по его ID.
     */
    public String getStaffName(String companyId, Long staffId) {
        return dataService.getStaffName(companyId, staffId);
    }

    /**
     * Возвращает название услуги по её ID.
     */
    public String getServiceName(String companyId, Long serviceId) {
        return dataService.getServiceName(companyId, serviceId);
    }

    /**
     * Запрашивает доступные временные слоты для выбранного мастера, даты и списка услуг.
     */
    public BookTimeResponse getAvailableTimes(String companyId, Long staffId, String date, List<Long> serviceIds) {
        return httpClient.getAvailableTimes(companyId, staffId, date, serviceIds, partnerToken);
    }

    /**
     * Запрашивает доступные даты для бронирования.
     */
    public ApiResponse<BookDatesData> getAvailableBookingDates(String companyId, Long staffId, List<Long> serviceIds) {
        return httpClient.getAvailableBookingDates(companyId, staffId, serviceIds);
    }

    /**
     * Инициирует процесс создания записи в системе Yclients.
     * <p>
     * Метод формирует структуру бронирования (appointment) и отправляет её в API.
     * При успешном создании извлекает record_id и record_hash для последующего управления записью.
     *
     * @param data    объект с данными о выбранном филиале, мастере, времени и услугах.
     * @param client  данные клиента (имя, телефон).
     * @param smsCode код из SMS (передается при повторном вызове после ввода кода пользователем).
     * @return true, если запись подтверждена и создана.
     * @throws YclientsSmsConfirmationException если Yclients требует проверку телефона через SMS.
     */
    public boolean createBooking(BookingData data, Client client, String smsCode) throws JsonProcessingException {
        Optional<Barbershop> barbershopOpt = barbershopService.getBySlug(data.getSlug());
        if (barbershopOpt.isEmpty()) {
            log.warn("Попытка записи в несуществующий филиал: {}", data.getSlug());
            return false;
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
        String formattedDatetime = data.getDatetime().format(formatter);
        String companyId = barbershopOpt.get().getYclientsCompanyId();

        // Формирование структуры запроса
        Map<String, Object> bookingPayload = new HashMap<>();
        bookingPayload.put("phone", client.getPhone());
        bookingPayload.put("fullname", client.getFullName());
        bookingPayload.put("email", client.getEmail());

        Map<String, Object> appointment = new HashMap<>();
        appointment.put("id", 1);
        appointment.put("services", data.getServiceIds());
        appointment.put("staff_id", data.getStaffId());
        appointment.put("datetime", formattedDatetime);

        bookingPayload.put("appointments", List.of(appointment));

        if (smsCode != null) {
            bookingPayload.put("code", smsCode);
        }

        try {
            String result = httpClient.postBooking(companyId, bookingPayload, partnerToken);
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(result);
            JsonNode dataNode = root.path("data");

            if (!dataNode.isMissingNode() && dataNode.isArray() && dataNode.size() > 0) {
                JsonNode firstRecord = dataNode.get(0);
                // Сохраняем ID и Hash для возможности отмены в будущем
                data.setRecordId(firstRecord.path("record_id").asLong());
                data.setRecordHash(firstRecord.path("record_hash").asText());
                return true;
            }
            return false;
        } catch (WebClientResponseException e) {
            if (e.getRawStatusCode() == 422 && e.getResponseBodyAsString().contains("\"code\":432")) {
                log.info("Yclients запросил SMS-подтверждение для клиента {}", client.getPhone());
                throw new YclientsSmsConfirmationException();
            }
            log.error("Ошибка API при создании записи: {}", e.getResponseBodyAsString());
            return false;
        } catch (Exception e) {
            log.error("Непредвиденная ошибка при бронировании", e);
            return false;
        }
    }

    /**
     * Отменяет существующую запись в системе Yclients.
     *
     * @param recordId   уникальный ID записи.
     * @param recordHash защитный хеш записи.
     * @return true, если запись была успешно удалена.
     */
    public boolean cancelBooking(Long recordId, String recordHash) {
        try {
            String url = String.format("/user/records/%d/%s", recordId, recordHash);
            httpClient.deleteBooking(url, partnerToken, userToken);
            return true;
        } catch (Exception e) {
            log.error("Не удалось отменить запись {}: {}", recordId, e.getMessage());
            return false;
        }
    }
}