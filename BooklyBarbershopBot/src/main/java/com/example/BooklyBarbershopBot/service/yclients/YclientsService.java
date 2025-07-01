package com.example.BooklyBarbershopBot.service.yclients;

import com.example.BooklyBarbershopBot.dto.ApiResponse;
import com.example.BooklyBarbershopBot.dto.BookDatesData;
import com.example.BooklyBarbershopBot.dto.BookTimeResponse;
import com.example.BooklyBarbershopBot.entity.Barbershop;
import com.example.BooklyBarbershopBot.service.BarbershopService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class YclientsService {

    private WebClient webClient;
    // Кеш для ускорения и избежания частых запросов
    private Map<Long, String> staffNamesCache = new ConcurrentHashMap<>();
    private Map<Long, String> serviceNamesCache = new ConcurrentHashMap<>();
    private final BarbershopService barbershopService;


    @Value("${yclients.partner-token}")
    private String partnerToken;

    @Value("${yclients.user-token}")
    private String userToken;

    @PostConstruct
    public void init() {
        this.webClient = WebClient.builder()
                .baseUrl("https://api.yclients.com/api/v1")
                .defaultHeader("Authorization", "Bearer " + partnerToken + ", User " + userToken)
                .defaultHeader("Accept", "application/vnd.yclients.v2+json")
                .build();
    }

    public String getServices(String companyId) {
        return webClient.get()
                .uri("/company/{companyId}/services", companyId)
                .header("Accept", "application/vnd.yclients.v2+json") // важно!
                .retrieve()
                .bodyToMono(String.class)
                .doOnNext(body -> log.info("Ответ YClients:\n{}", body))
                .block();
    }

    // Получить список мастеров для конкретного барбершопа
    public String getStaff(String companyId) {
        return webClient.get()
                .uri("/book_staff/{companyId}", companyId)
                .header("Accept", "application/vnd.yclients.v2+json") // важно
                .retrieve()
                .bodyToMono(String.class)
                .doOnNext(body -> log.info("Ответ YClients:\n{}", body))
                .block();
    }

    // Даты доступные для бронирования
    public ApiResponse<BookDatesData> getAvailableBookingDates(String companyId, Long staffId, Long serviceId) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/book_dates/" + companyId)
                        .queryParam("staff_id", staffId)
                        .queryParam("service_ids[]", serviceId)
                        .build())
                .header("Accept", "application/vnd.yclients.v2+json")
                .header("Authorization", "Bearer " + partnerToken)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<BookDatesData>>() {})
                .doOnNext(response -> log.info("Доступные даты бронирования: {}", response))
                .block();
    }

    public BookTimeResponse getAvailableTimes(String companyId, Long staffId, String date, Long serviceId) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/book_times/" + companyId + "/" + staffId + "/" + date)
                        .queryParam("service_ids[]", serviceId)
                        .build())
                .header("Accept", "application/vnd.yclients.v2+json")
                .header("Authorization", "Bearer " + partnerToken)
                .retrieve()
                .bodyToMono(BookTimeResponse.class)
                .doOnNext(response -> log.info("⏱ Доступные сеансы: {}", response))
                .block();
    }

//    public boolean createBooking(String slug, Long serviceId, Long staffId, String datetime, String phone) {
//        // TODO: Подготовить JSON и сделать POST-запрос
//        log.info("📦 Создание записи в Yclients: {}, {}, {}, {}, {}", slug, serviceId, staffId, datetime, phone);
//        return true; // пока фейковый успех
//    }

    //Получение имени мастера по staffId
    public String getStaffName(String companyId, Long staffId) {
        String url = String.format("/staff/%s", companyId);

        try {
            String response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            // Преобразуем JSON вручную
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response);
            for (JsonNode staff : root.get("data")) {
                if (staff.get("id").asLong() == staffId) {
                    return staff.get("name").asText();
                }
            }

        } catch (Exception e) {
            log.error("❌ Ошибка при получении имени мастера", e);
        }

        return "Неизвестный мастер";
    }

    //Получение имени услуги по serviceId
    public String getServiceName(String companyId, Long serviceId) {
        String url = String.format("/services/%s", companyId);

        try {
            String response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response);
            for (JsonNode service : root.get("data")) {
                if (service.get("id").asLong() == serviceId) {
                    return service.get("title").asText();
                }
            }

        } catch (Exception e) {
            log.error("❌ Ошибка при получении названия услуги", e);
        }

        return "Неизвестная услуга";
    }

    public boolean createBooking(String slug, Long serviceId, Long staffId, String datetime, String phone) throws JsonProcessingException {
        Optional<Barbershop> optionalBarbershop = barbershopService.getBySlug(slug);
        if (optionalBarbershop.isEmpty()) return false;

        String companyId = optionalBarbershop.get().getYclientsCompanyId();

        Map<String, Object> bookingPayload = new HashMap<>();
        //FIXME ВРЕМЕННЫЕ ДАННЫЕ
        bookingPayload.put("phone", phone);
        bookingPayload.put("fullname", "Клиент Telegram");
        bookingPayload.put("email", "no-reply@example.com");

        //FIXME ВРЕМЕННЫЙ ЛОГ + throws JsonProcessingException
        log.info("📦 Booking Payload: {}", new ObjectMapper().writeValueAsString(bookingPayload));


        Map<String, Object> appointment = new HashMap<>();
        appointment.put("id", 1);
        appointment.put("services", List.of(serviceId));
        appointment.put("staff_id", staffId);
        appointment.put("datetime", datetime);

        bookingPayload.put("appointments", List.of(appointment));

        //FIXME ВРЕМЕННЫЙ ЛОГ + throws JsonProcessingException
        log.info("📦 Booking Payload: {}", new ObjectMapper().writeValueAsString(bookingPayload));


        try {
            String result = webClient.post()
                    .uri("/book_record/" + companyId)
                    .header("Authorization", "Bearer " + partnerToken)
                    .header("Accept", "application/vnd.yclients.v2+json")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(bookingPayload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .doOnNext(body -> log.info("Ответ на создание записи: {}", body))
                    .block();

            return true;
        } catch (WebClientResponseException e) {
            log.error("Ошибка при создании записи. Код: {}, Тело: {}", e.getRawStatusCode(), e.getResponseBodyAsString());
            return false;
        } catch (Exception e) {
            log.error("Ошибка при создании записи", e);
            return false;
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
}
