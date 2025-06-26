package com.example.BooklyBarbershopBot.service.yclients;

import com.example.BooklyBarbershopBot.dto.BookDatesResponse;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class YclientsService {

    private WebClient webClient;
    private final Map<String, String> categoryCache = new ConcurrentHashMap<>();


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
    public BookDatesResponse getAvailableBookingDates(String companyId, Long staffId, Long serviceId) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .defaultHeader("Accept", "application/vnd.yclients.v2+json")
                        .path("/book_dates/" + companyId)
                        .queryParam("staff_id", staffId)
                        .queryParam("service_ids", serviceId)
                        .build())
                .header("Accept", "application/vnd.yclients.v2+json")
                .retrieve()
                .bodyToMono(BookDatesResponse.class)
                .doOnNext(response -> log.info("Доступные даты бронирования: {}", response))
                .block();
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
