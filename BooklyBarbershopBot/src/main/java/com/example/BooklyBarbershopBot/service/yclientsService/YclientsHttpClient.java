package com.example.BooklyBarbershopBot.service.yclientsService;

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

import java.util.List;
import java.util.Map;

/**
 * Низкоуровневый HTTP-клиент для прямого взаимодействия с API Yclients.
 * <p>
 * Класс инкапсулирует логику авторизации по схеме Bearer + User Token и реализует
 * основные методы протокола Yclients: получение справочников, поиск слотов и создание записей.
 */
@SuppressWarnings("deprecation")
@Slf4j
@Service
@RequiredArgsConstructor
public class YclientsHttpClient {

    private WebClient webClient;

    @Value("${YCLIENTS_PARTNER_TOKEN}")
    private String partnerToken;

    @Value("${YCLIENTS_USER_TOKEN}")
    private String userToken;

    @Value("${YCLIENTS_BASE_URL:https://api.yclients.com/api/v1}")
    private String baseUrl;

    /**
     * Конфигурирует WebClient после инициализации бина.
     * Устанавливает базовый URL и обязательные заголовки для API версии 2.
     */
    @PostConstruct
    public void init() {
        this.webClient = WebClient.builder().baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + partnerToken + ", User " + userToken)
                .defaultHeader("Accept", "application/vnd.yclients.v2+json")
                .build();
    }

    /**
     * Запрашивает полный список услуг компании.
     */
    public String getServicesRaw(String companyId) {
        return webClient.get()
                .uri("/company/{companyId}/services", companyId)
                .retrieve()
                .bodyToMono(String.class)
                .doOnNext(body -> log.info("Загружены услуги для компании {}", companyId))
                .block();
    }

    /**
     * Запрашивает список сотрудников, доступных для бронирования в филиале.
     */
    public String getStaffRaw(String companyId) {
        return webClient.get()
                .uri("/book_staff/{companyId}", companyId)
                .retrieve()
                .bodyToMono(String.class)
                .doOnNext(body -> log.info("Загружен список мастеров для компании {}", companyId))
                .block();
    }

    /**
     * Получает список дат, на которые возможна запись к конкретному мастеру на определенные услуги.
     *
     * @param companyId  ID филиала.
     * @param staffId    ID мастера.
     * @param serviceIds список ID услуг.
     * @return {@link ApiResponse} с вложенными датами бронирования.
     */
    public ApiResponse<BookDatesData> getAvailableBookingDates(String companyId, Long staffId, List<Long> serviceIds) {
        try {
            return webClient.get().uri(uriBuilder -> {
                        var builder = uriBuilder.path("/book_dates/" + companyId).queryParam("staff_id", staffId);
                        for (Long id : serviceIds) {
                            builder.queryParam("service_ids[]", id);
                        }
                        return builder.build();
                    }).retrieve()
                    .bodyToMono(new ParameterizedTypeReference<ApiResponse<BookDatesData>>() {})
                    .doOnNext(response -> log.debug("Получены доступные даты для мастера {}", staffId))
                    .block();
        } catch (WebClientResponseException e) {
            log.error("Ошибка API при получении дат: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Запрашивает доступные временные интервалы (сеансы) на выбранную дату.
     */
    public BookTimeResponse getAvailableTimes(String companyId, Long staffId, String date, List<Long> serviceIds, String partnerToken) {
        this.partnerToken = partnerToken;
        try {
            return webClient.get().uri(uriBuilder -> {
                        var builder = uriBuilder.path("/book_times/" + companyId + "/" + staffId + "/" + date);
                        for (Long serviceId : serviceIds) {
                            builder = builder.queryParam("service_ids[]", serviceId);
                        }
                        return builder.build();
                    }).retrieve()
                    .bodyToMono(BookTimeResponse.class)
                    .doOnNext(response -> log.debug("Получены временные слоты на дату {}", date))
                    .block();
        } catch (WebClientResponseException e) {
            log.error("Ошибка API при получении времени: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Создает новую запись (бронирование) в системе Yclients.
     *
     * @param companyId ID филиала.
     * @param payload   карта с данными (fullname, phone, datetime, services и т.д.).
     * @param partnerToken динамический токен партнера.
     * @return JSON-строка с результатом создания записи (record_id).
     */
    public String postBooking(String companyId, Map<String, Object> payload, String partnerToken) {
        return webClient.post()
                .uri("/book_record/" + companyId)
                .header("Authorization", "Bearer " + partnerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(String.class)
                .doOnNext(response -> log.info("Создана запись в YClients для компании {}", companyId))
                .block();
    }

    /**
     * Удаляет (отменяет) существующую запись.
     */
    public String deleteBooking(String url, String partnerToken, String userToken) {
        return webClient.delete()
                .uri(url)
                .header("Authorization", "Bearer " + partnerToken + ", User " + userToken)
                .header("Accept", "application/vnd.yclients.v2+json")
                .header("Content-Type", "application/json")
                .retrieve()
                .bodyToMono(String.class)
                .doOnNext(res -> log.info("Запись успешно отменена по URL: {}", url))
                .block();
    }
}