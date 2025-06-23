package com.example.BooklyBarbershopBot.service.yclients;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
@RequiredArgsConstructor
@Slf4j
public class YclientsService {

    private WebClient webClient;

    @Value("${yclients.partner-token}")
    private String partnerToken;

    @Value("${yclients.user-token}")
    private String userToken;

//    public YclientsService(String partnerToken) {
//        this.partnerToken = partnerToken;
//        this.webClient = WebClient.builder()
//                .baseUrl("https://api.yclients.com/api/v1")
//                .defaultHeader("Authorization", "Bearer " + partnerToken)
//                .build();
//    }

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
}
