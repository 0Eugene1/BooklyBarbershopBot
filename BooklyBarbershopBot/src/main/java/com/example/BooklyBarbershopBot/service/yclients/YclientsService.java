package com.example.BooklyBarbershopBot.service.yclients;

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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
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
    @Value("${yclients.user-token}")
    private String userToken;


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

    public boolean createBooking(BookingData data, Client client, String smsCode) throws JsonProcessingException {
        Optional<Barbershop> barbershopOpt = barbershopService.getBySlug(data.getSlug());
        if (barbershopOpt.isEmpty()) {
            log.warn("Барбершоп с слагом {} не найден", data.getSlug());
            return false;
        }

        String companyId = barbershopOpt.get().getYclientsCompanyId();

        Map<String, Object> bookingPayload = new HashMap<>();
        bookingPayload.put("phone", client.getPhone());
        log.info("Отправляем в Yclients fullname: {}", client.getFullName());
        bookingPayload.put("fullname", client.getFullName());
        bookingPayload.put("email", client.getEmail());

        Map<String, Object> appointment = new HashMap<>();
        appointment.put("id", 1);
        appointment.put("services", List.of(data.getServiceId()));
        appointment.put("staff_id", data.getStaffId());
        appointment.put("datetime", data.getDatetime());

        bookingPayload.put("appointments", List.of(appointment));

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

