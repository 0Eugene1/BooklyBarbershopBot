package com.example.BooklyBarbershopBot.service.yclients;

import com.example.BooklyBarbershopBot.dto.ApiResponse;
import com.example.BooklyBarbershopBot.dto.BookDatesData;
import com.example.BooklyBarbershopBot.dto.BookTimeResponse;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class YclientsHttpClient {

    private WebClient webClient;

    @Value("${yclients.partner-token}")
    private String partnerToken;

    @Value("${yclients.user-token}")
    private String userToken;

    @Value("${yclients.base-url:https://api.yclients.com/api/v1}")
    private String baseUrl;

    @PostConstruct
    public void init() {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + partnerToken + ", User " + userToken)
                .defaultHeader("Accept", "application/vnd.yclients.v2+json")
                .build();
    }

    public String getServicesRaw(String companyId) {
        return webClient.get()
                .uri("/company/{companyId}/services", companyId)
                .retrieve()
                .bodyToMono(String.class)
                .doOnNext(body -> log.info("Ответ YClients (services): {}", body))
                .block();
    }

    public String getStaffRaw(String companyId) {
        return webClient.get()
                .uri("/book_staff/{companyId}", companyId)
                .retrieve()
                .bodyToMono(String.class)
                .doOnNext(body -> log.info("Ответ YClients (staff): {}", body))
                .block();
    }

    public ApiResponse<BookDatesData> getAvailableBookingDates(String companyId, Long staffId, Long serviceId, String partnerToken) {
        try {
            return webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/book_dates/" + companyId)
                            .queryParam("staff_id", staffId)
                            .queryParam("service_ids[]", serviceId)
                            .build())
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<ApiResponse<BookDatesData>>() {})
                    .doOnNext(response -> log.info("Доступные даты бронирования: {}", response))
                    .block();
        } catch (WebClientResponseException e) {
            log.error("Ошибка при запросе дат бронирования. Код: {}, тело: {}", e.getRawStatusCode(), e.getResponseBodyAsString());
            throw e;
        }
    }

    public BookTimeResponse getAvailableTimes(String companyId, Long staffId, String date, Long serviceId, String partnerToken) {
        try {
            return webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/book_times/" + companyId + "/" + staffId + "/" + date)
                            .queryParam("service_ids[]", serviceId)
                            .build())
                    .retrieve()
                    .bodyToMono(BookTimeResponse.class)
                    .doOnNext(response -> log.info("Доступные сеансы: {}", response))
                    .block();
        } catch (WebClientResponseException e) {
            log.error("Ошибка при запросе времени бронирования. Код: {}, тело: {}", e.getRawStatusCode(), e.getResponseBodyAsString());
            throw e;
        }
    }

    public String postBooking(String companyId, Map<String, Object> payload, String partnerToken) {
        return webClient.post()
                .uri("/book_record/" + companyId)
                .header("Authorization", "Bearer " + partnerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(String.class)
                .doOnNext(response -> log.info("📩 Ответ от YClients на бронирование: {}", response))
                .block();
    }

    //Метод отмены брони
    public String deleteBooking(String url, String partnerToken, String userToken) {
        return webClient.delete()
                .uri(url)
                .header("Authorization", "Bearer " + partnerToken + ", User " + userToken)
                .header("Accept", "application/vnd.yclients.v2+json")
                .header("Content-Type", "application/json")
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

}
