package com.example.BooklyBarbershopBot.service.yclients;

import com.example.BooklyBarbershopBot.dto.*;
import com.example.BooklyBarbershopBot.entity.Barbershop;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class YclientsService {

    private final YclientsHttpClient httpClient;
    private final YclientsDataService dataService;
    private final BarbershopService barbershopService;

    @Value("${yclients.partner-token}")
    private String partnerToken;

    public List<StaffDto> getStaff(String companyId) {
        // просто делегируем
        return dataService.getStaffList(companyId);
    }

    public List<ServiceDto> getServices(String companyId) {
        return dataService.getServiceList(companyId);
    }

    public String getStaffName(String companyId, Long staffId) {
        return dataService.getStaffName(companyId, staffId);
    }

    public String getServiceName(String companyId, Long serviceId) {
        return dataService.getServiceName(companyId, serviceId);
    }

    public BookTimeResponse getAvailableTimes(String companyId, Long staffId, String date, Long serviceId) {
        return httpClient.getAvailableTimes(companyId, staffId, date, serviceId, partnerToken);
    }

    public ApiResponse<BookDatesData> getAvailableBookingDates(String companyId, Long staffId, Long serviceId) {
        return httpClient.getAvailableBookingDates(companyId, staffId, serviceId, partnerToken);
    }

    public String getStaffRawJson(String companyId) {
        return httpClient.getStaffRaw(companyId);
    }

    public String getServicesRawJson(String companyId) {
        return httpClient.getServicesRaw(companyId);
    }


    // Получение дат и времени можно оставить здесь, либо тоже делегировать, если хочется

    public Optional<Long> createBooking(String slug, Long serviceId, Long staffId, String datetime, String phone) throws JsonProcessingException {
        Optional<Barbershop> barbershopOpt = barbershopService.getBySlug(slug);
        if (barbershopOpt.isEmpty()) {
            log.warn("Барбершоп с слагом {} не найден", slug);
            return Optional.empty();
        }

        String companyId = barbershopOpt.get().getYclientsCompanyId();

        Map<String, Object> bookingPayload = new HashMap<>();
        bookingPayload.put("phone", phone);
        bookingPayload.put("fullname", "Клиент Telegram");
        bookingPayload.put("email", "no-reply@example.com");

        Map<String, Object> appointment = new HashMap<>();
        appointment.put("id", 1);
        appointment.put("services", List.of(serviceId));
        appointment.put("staff_id", staffId);
        appointment.put("datetime", datetime);

        bookingPayload.put("appointments", List.of(appointment));

        try {
            String result = httpClient.postBooking(companyId, bookingPayload, partnerToken);
            log.info("Ответ на создание записи: {}", result);

            // 👉 Парсим ответ и извлекаем record_id
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(result);

            if (root.path("success").asBoolean()) {
                long recordId = root.path("data").path("id").asLong();
                log.info("✅ Запись успешно создана. record_id = {}", recordId);
                return Optional.of(recordId);
            } else {
                log.warn("⚠️ Запись не создана. Ответ: {}", result);
                return Optional.empty();
            }

        } catch (WebClientResponseException e) {
            log.error("Ошибка при создании записи. Код: {}, Тело: {}", e.getRawStatusCode(), e.getResponseBodyAsString());
            if (e.getRawStatusCode() == 422 && e.getResponseBodyAsString().contains("\"code\":432")) {
                throw new YclientsSmsConfirmationException();
            }
            return Optional.empty();
        } catch (Exception e) {
            log.error("Ошибка при создании записи", e);
            return Optional.empty();
        }
    }

    public boolean cancelBooking(String slug, Long recordId) {
        Optional<Barbershop> barbershopOpt = barbershopService.getBySlug(slug);
        if (barbershopOpt.isEmpty()) {
            log.warn("Барбершоп с слагом {} не найден", slug);
            return false;
        }

        String companyId = barbershopOpt.get().getYclientsCompanyId();

        try {
            String result = httpClient.deleteBooking(companyId, recordId, partnerToken);
            log.info("🗑 Ответ на удаление записи: {}", result);
            return true;
        } catch (WebClientResponseException e) {
            log.error("Ошибка при удалении записи. Код: {}, Тело: {}", e.getRawStatusCode(), e.getResponseBodyAsString());
            return false;
        } catch (Exception e) {
            log.error("Ошибка при удалении записи", e);
            return false;
        }
    }
}









//    //Метод получения одной категории услуг
//    public CategoryDto getCategory(String companyId, String categoryId) {
//        String json = webClient.get()
//                .uri("/company/{companyId}/service_categories", companyId, categoryId)
//                .retrieve()
//                .bodyToMono(String.class)
//                .doOnNext(body -> log.info("Категория {}: {}", categoryId, body))
//                .block();
//
//        try {
//            ObjectMapper mapper = new ObjectMapper();
//            return mapper.readValue(json, CategoryDto.class);
//        } catch (Exception e) {
//            log.error("Ошибка при парсинге категории {}", categoryId, e);
//            return null;
//        }
//    }
//
//    //computeIfAbsent вызовет getCategory() только один раз на категорию.
//    public String getCategoryNameCached(String companyId, String categoryId) {
//        return categoryCache.computeIfAbsent(categoryId, id -> {
//            CategoryDto dto = getCategory(companyId, id);
//            return dto != null ? dto.getTitle() : "Категория " + id;
//        });
//    }

