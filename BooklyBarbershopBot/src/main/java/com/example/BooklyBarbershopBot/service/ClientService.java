package com.example.BooklyBarbershopBot.service;

import com.example.BooklyBarbershopBot.entity.Client;
import com.example.BooklyBarbershopBot.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Сервис для работы с сущностью Client.
 * Отвечает за создание новых клиентов и обновление существующих,
 * а также поиск клиента по Telegram ID.
 */
@Service
@RequiredArgsConstructor
public class ClientService {
    private final ClientRepository clientRepository;

    /**
     * Создает нового клиента или возвращает существующего по номеру телефона.
     * Если клиент уже есть, обновляет отсутствующие данные: полное имя, email, telegramId.
     *
     * @param phone номер телефона клиента (используется как уникальный идентификатор)
     * @param telegramId ID Telegram пользователя (может быть null)
     * @param fullName полное имя клиента (может быть null)
     * @param email email клиента (может быть null)
     * @return сохраненный или найденный клиент
     */
    public Client saveOrGetClient(String phone, Long telegramId, String fullName, String email) {

        Optional<Client> existingClientOpt = clientRepository.findByPhone(phone);
        if (existingClientOpt.isPresent()) {
            Client existingClient = existingClientOpt.get();

            // Обновляем данные, если нужно
            boolean updated = false;
            if ((existingClient.getFullName() == null || existingClient.getFullName().isEmpty()) && fullName != null && !fullName.isEmpty()) {
                existingClient.setFullName(fullName);
                updated = true;
            }
            if ((existingClient.getEmail() == null || existingClient.getEmail().isEmpty()) && email != null && !email.isEmpty()) {
                existingClient.setEmail(email);
                updated = true;
            }
            if (existingClient.getTelegramId() == null && telegramId != null) {
                existingClient.setTelegramId(telegramId);
                updated = true;
            }

            if (updated) {
                return clientRepository.save(existingClient);
            } else {
                return existingClient;
            }
        } else {
            Client newClient = Client.builder()
                    .phone(phone)
                    .telegramId(telegramId)
                    .fullName(fullName)
                    .email(email)
                    .build();
            return clientRepository.save(newClient);
        }
    }

    /**
     * Найти клиента по Telegram ID.
     *
     * @param telegramId ID Telegram пользователя
     * @return Optional с клиентом, если найден
     */
    public Optional<Client> findByTelegramId(Long telegramId) {
        return clientRepository.findByTelegramId(telegramId);
    }

    public void saveOrUpdateLastUsedSlug(Long telegramId, String slug) {
        findByTelegramId(telegramId).ifPresentOrElse(
                client -> {
                    client.setLastUsedSlug(slug);
                    clientRepository.save(client);
                },
                () -> {
                    Client newClient = Client.builder()
                            .telegramId(telegramId)
                            .lastUsedSlug(slug)
                            .build();
                    clientRepository.save(newClient);
                }
        );
    }

}