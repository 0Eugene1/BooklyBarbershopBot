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

import java.util.List;
import java.util.Map;

/**
 * HTTP клиент для работы с API Yclients.
 * Использует WebClient для выполнения REST-запросов с авторизацией по токенам партнера и пользователя.
 * Поддерживает операции получения мастеров, услуг, доступных дат и времени записи, а также бронирование и отмену записи.
 */
@SuppressWarnings("deprecation")
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

    /**
     * Инициализация WebClient с базовым URL и заголовками авторизации.
     */
    @PostConstruct
    public void init() {
        this.webClient = WebClient.builder().baseUrl(baseUrl).defaultHeader("Authorization", "Bearer " + partnerToken + ", User " + userToken).defaultHeader("Accept", "application/vnd.yclients.v2+json").build();
    }

    /**
     * Получить сырые данные услуг компании.
     *
     * @param companyId ID компании Yclients
     * @return JSON строка с данными услуг
     */
    public String getServicesRaw(String companyId) {
        return webClient.get().uri("/company/{companyId}/services", companyId).retrieve().bodyToMono(String.class).doOnNext(body -> log.info("Ответ YClients (services): {}", body)).block();
    }

    /**
     * Получить сырые данные мастеров компании.
     *
     * @param companyId ID компании Yclients
     * @return JSON строка с данными мастеров
     */
    public String getStaffRaw(String companyId) {
        return webClient.get().uri("/book_staff/{companyId}", companyId).retrieve().bodyToMono(String.class).doOnNext(body -> log.info("Ответ YClients (staff): {}", body)).block();
    }

    /**
     * Получить доступные даты для бронирования по параметрам.
     *
     * @param companyId  ID компании
     * @param staffId    ID мастера
     * @param serviceIds ID услуги
     * @return объект с данными о доступных датах
     */
    //FIXME TEST 11.08
    public ApiResponse<BookDatesData> getAvailableBookingDates(String companyId, Long staffId, List<Long> serviceIds) {
        try {
            return webClient.get().uri(uriBuilder -> {
                var builder = uriBuilder.path("/book_dates/" + companyId).queryParam("staff_id", staffId);

                // добавляем каждый serviceId как отдельный service_ids[]
                for (Long id : serviceIds) {
                    builder.queryParam("service_ids[]", id);
                }

                return builder.build();
            }).retrieve().bodyToMono(new ParameterizedTypeReference<ApiResponse<BookDatesData>>() {
            }).doOnNext(response -> log.info("Доступные даты бронирования: {}", response)).block();

        } catch (WebClientResponseException e) {
            log.error("Ошибка при запросе дат бронирования. Код: {}, тело: {}", e.getRawStatusCode(), e.getResponseBodyAsString());
            throw e;
        }
    }

    /**
     * Получить доступное время для бронирования.
     *
     * @param companyId    ID компании
     * @param staffId      ID мастера
     * @param date         дата в формате yyyy-MM-dd
     * @param serviceIds   ID услуги
     * @param partnerToken токен партнёра для авторизации
     * @return объект с данными о доступных временных слотах
     */
    //FIXME TEST 11.08
    public BookTimeResponse getAvailableTimes(String companyId, Long staffId, String date, List<Long> serviceIds, String partnerToken) {
        this.partnerToken = partnerToken;
        try {
            return webClient.get().uri(uriBuilder -> {
                var builder = uriBuilder.path("/book_times/" + companyId + "/" + staffId + "/" + date);
                for (Long serviceId : serviceIds) {
                    builder = builder.queryParam("service_ids[]", serviceId);
                }
                return builder.build();
            }).retrieve().bodyToMono(BookTimeResponse.class).doOnNext(response -> log.info("Доступные сеансы: {}", response)).block();
        } catch (WebClientResponseException e) {
            log.error("Ошибка при запросе времени бронирования. Код: {}, тело: {}", e.getRawStatusCode(), e.getResponseBodyAsString());
            throw e;
        }
    }

    /**
     * Отправить POST-запрос для создания бронирования.
     *
     * @param companyId    ID компании
     * @param payload      тело запроса с данными бронирования
     * @param partnerToken токен партнёра для авторизации
     * @return ответ в виде строки JSON
     */
    public String postBooking(String companyId, Map<String, Object> payload, String partnerToken) {
        return webClient.post().uri("/book_record/" + companyId).header("Authorization", "Bearer " + partnerToken).contentType(MediaType.APPLICATION_JSON).bodyValue(payload).retrieve().bodyToMono(String.class).doOnNext(response -> log.info("📩 Ответ от YClients на бронирование: {}", response)).block();
    }

    /**
     * Отменить бронирование по URL.
     *
     * @param url          полный URL для удаления записи
     * @param partnerToken токен партнёра
     * @param userToken    токен пользователя
     * @return ответ сервера в виде строки JSON
     */
    public String deleteBooking(String url, String partnerToken, String userToken) {
        return webClient.delete().uri(url).header("Authorization", "Bearer " + partnerToken + ", User " + userToken).header("Accept", "application/vnd.yclients.v2+json").header("Content-Type", "application/json").retrieve().bodyToMono(String.class).block();
    }
}