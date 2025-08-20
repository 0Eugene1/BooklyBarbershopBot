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
 * Сервис для взаимодействия с API Yclients.
 * Инкапсулирует логику получения мастеров, услуг, доступных дат и времени,
 * а также создание и отмену бронирований через HTTP клиент.
 */
@SuppressWarnings("ALL")
@Service
@RequiredArgsConstructor
@Slf4j
public class YclientsService {

    private final YclientsHttpClient httpClient;
    private final YclientsDataService dataService;
    private final BarbershopService barbershopService;

    /**
     * Токен партнёра для авторизации в Yclients API.
     * Внедряется из настроек приложения.
     */
    @Value("${yclients.partner-token}")
    private String partnerToken;
    /**
     * Токен пользователя для авторизации в Yclients API.
     * Внедряется из настроек приложения.
     */
    @Value("${yclients.user-token}")
    private String userToken;

    public List<StaffDto> getStaff(String companyId) {
        return dataService.getStaffList(companyId); // с обновлением кэша
    }

    public List<StaffDto> getFreshStaff(String companyId) {
        return dataService.getStaffListFresh(companyId); // без обновления кэша
    }


    /**
     * Получить список услуг компании Yclients.
     *
     * @param companyId ID компании
     * @return список DTO услуг
     */
    public List<ServiceDto> getServices(String companyId) {
        return dataService.getServiceList(companyId);
    }

    /**
     * Получить имя мастера по ID.
     *
     * @param companyId ID компании
     * @param staffId   ID мастера
     * @return имя мастера или "Неизвестный мастер", если не найден
     */
    public String getStaffName(String companyId, Long staffId) {
        return dataService.getStaffName(companyId, staffId);
    }

    /**
     * Получить название услуги по ID.
     *
     * @param companyId ID компании
     * @param serviceId ID услуги
     * @return название услуги или "Неизвестная услуга", если не найдена
     */
    public String getServiceName(String companyId, Long serviceId) {
        return dataService.getServiceName(companyId, serviceId);
    }

    /**
     * Получить доступное время для записи.
     *
     * @param companyId ID компании
     * @param staffId   ID мастера
     * @param date      дата в формате yyyy-MM-dd
     * @param serviceId ID услуги
     * @return объект с доступным временем записи
     */
    //FIXME TEST 11.08
    public BookTimeResponse getAvailableTimes(String companyId, Long staffId, String date, List<Long> serviceIds) {
        return httpClient.getAvailableTimes(companyId, staffId, date, serviceIds, partnerToken);
    }

//    public BookTimeResponse getAvailableTimes(String companyId, Long staffId, String date, Long serviceId) {
//        return httpClient.getAvailableTimes(companyId, staffId, date, serviceId, partnerToken);
//    }

    /**
     * Получить доступные даты для бронирования.
     *
     * @param companyId ID компании
     * @param staffId   ID мастера
     * @param serviceId ID услуги
     * @return ApiResponse с данными доступных дат
     */
    public ApiResponse<BookDatesData> getAvailableBookingDates(String companyId, Long staffId, Long serviceId) {
        return httpClient.getAvailableBookingDates(companyId, staffId, Collections.singletonList(serviceId));
    }

    //FIXME TEST 11.08
    public ApiResponse<BookDatesData> getAvailableBookingDates(String companyId, Long staffId, List<Long> serviceIds) {
        return httpClient.getAvailableBookingDates(companyId, staffId, serviceIds);
    }

    /**
     * Получить сырые JSON данные мастеров (необработанные).
     *
     * @param companyId ID компании
     * @return JSON строка с данными мастеров
     */
    public String getStaffRawJson(String companyId) {
        return httpClient.getStaffRaw(companyId);
    }

    /**
     * Получить сырые JSON данные услуг (необработанные).
     *
     * @param companyId ID компании
     * @return JSON строка с данными услуг
     */
    public String getServicesRawJson(String companyId) {
        return httpClient.getServicesRaw(companyId);
    }

    /**
     * Создать бронирование записи в Yclients.
     * Если для подтверждения требуется SMS-код, бросает YclientsSmsConfirmationException.
     *
     * @param data    данные для бронирования
     * @param client  клиент, делающий бронирование
     * @param smsCode код подтверждения из SMS, если есть
     * @return true, если бронирование успешно создано
     * @throws JsonProcessingException          при ошибках парсинга ответа
     * @throws YclientsSmsConfirmationException если требуется подтверждение SMS-кодом
     */
    // Получение дат и времени можно оставить здесь, либо тоже делегировать, если хочется
    public boolean createBooking(BookingData data, Client client, String smsCode) throws JsonProcessingException {
        Optional<Barbershop> barbershopOpt = barbershopService.getBySlug(data.getSlug());
        if (barbershopOpt.isEmpty()) {
            log.warn("Барбершоп с слагом {} не найден", data.getSlug());
            return false;
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
        String formattedDatetime = data.getDatetime().format(formatter);

        String companyId = barbershopOpt.get().getYclientsCompanyId();

        Map<String, Object> bookingPayload = new HashMap<>();
        bookingPayload.put("phone", client.getPhone());
        log.info("Отправляем в Yclients fullname: {}", client.getFullName());
        bookingPayload.put("fullname", client.getFullName());
        bookingPayload.put("email", client.getEmail());

        Map<String, Object> appointment = new HashMap<>();
        appointment.put("id", 1);
        //FIXME TEST 11.08
        appointment.put("services", data.getServiceIds());
        appointment.put("staff_id", data.getStaffId());
        appointment.put("datetime", formattedDatetime);

        bookingPayload.put("appointments", List.of(appointment));
        log.info("Создаваемая запись: staffId={}, services={}, datetime={}, fullname={}",
                data.getStaffId(), data.getServiceIds(), data.getDatetime(), client.getFullName());

        if (smsCode != null) {
            bookingPayload.put("code", smsCode);
        }

        try {
            String result = httpClient.postBooking(companyId, bookingPayload, partnerToken);
            log.info("Ответ на создание записи: {}", result);

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(result);
            JsonNode dataNode = root.path("data");

            if (!dataNode.isMissingNode() && dataNode.isArray() && dataNode.size() > 0) {
                JsonNode firstRecord = dataNode.get(0);
                Long recordId = firstRecord.path("record_id").asLong();
                String recordHash = firstRecord.path("record_hash").asText();

                data.setRecordId(recordId);
                data.setRecordHash(recordHash);

                return true;
            } else {
                log.warn("Ответ от Yclients не содержит данных записи.");
                return false;
            }
        } catch (WebClientResponseException e) {
            log.error("Ошибка при создании записи. Код: {}, Тело: {}", e.getRawStatusCode(), e.getResponseBodyAsString());
            if (e.getRawStatusCode() == 422 && e.getResponseBodyAsString().contains("\"code\":432")) {
                throw new YclientsSmsConfirmationException();
            }
            return false;
        } catch (Exception e) {
            log.error("Ошибка при создании записи", e);
            return false;
        }
    }

    /**
     * Отменить бронирование записи в Yclients.
     *
     * @param recordId   ID записи
     * @param recordHash hash записи для управления
     * @return true, если отмена прошла успешно
     */
    //Метод удаления
    public boolean cancelBooking(Long recordId, String recordHash) {
        try {
            String url = String.format("/user/records/%d/%s", recordId, recordHash);
            String result = httpClient.deleteBooking(url, partnerToken, userToken);
            log.info("Удаление записи успешно: {}", result);
            return true;
        } catch (WebClientResponseException e) {
            log.error("Ошибка при удалении записи. Код: {}, Тело: {}", e.getRawStatusCode(), e.getResponseBodyAsString());
            return false;
        }
    }
}