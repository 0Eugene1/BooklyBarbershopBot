package com.example.BooklyBarbershopBot.service;

import com.example.BooklyBarbershopBot.entity.Client;
import com.example.BooklyBarbershopBot.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Сервис для работы с сущностью Client.
 * Отвечает за создание новых клиентов и обновление существующих,
 * а также поиск клиента по Telegram ID.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ClientService {
    private final ClientRepository clientRepository;

    /**
     * Создает нового клиента или возвращает существующего по номеру телефона.
     * Если клиент уже есть, обновляет отсутствующие данные: полное имя, email, telegramId.
     *
     * @param phone      номер телефона клиента (используется как уникальный идентификатор)
     * @param telegramId ID Telegram пользователя (может быть null)
     * @param fullName   полное имя клиента (может быть null)
     * @param email      email клиента (может быть null)
     * @return сохраненный или найденный клиент
     */
    public Client saveOrGetClient(String phone, Long telegramId, String fullName, String email) {
        // Проверяем по telegram_id сначала
        Optional<Client> existingByTelegramId = findByTelegramId(telegramId);
        if (existingByTelegramId.isPresent()) {
            Client existing = existingByTelegramId.get();
            // Обновляем данные, если нужно
            boolean updated = false;
            if (fullName != null && !fullName.isEmpty() && (existing.getFullName() == null || !existing.getFullName().equals(fullName))) {
                existing.setFullName(fullName);
                updated = true;
            }
            if (phone != null && !phone.isEmpty() && (existing.getPhone() == null || !existing.getPhone().equals(phone))) {
                existing.setPhone(phone);
                updated = true;
            }
            if (email != null && !email.isEmpty() && (existing.getEmail() == null || !existing.getEmail().equals(email))) {
                existing.setEmail(email);
                updated = true;
            }
            if (updated) {
                return clientRepository.save(existing);
            }
            return existing;
        }

        // Если не найден по telegram_id, проверяем по phone
        Optional<Client> existingByPhone = clientRepository.findByPhone(phone);
        if (existingByPhone.isPresent()) {
            Client existing = existingByPhone.get();
            // Обновляем данные, включая telegram_id
            boolean updated = false;
            if (telegramId != null && (existing.getTelegramId() == null || !existing.getTelegramId().equals(telegramId))) {
                existing.setTelegramId(telegramId);
                updated = true;
            }
            // ... аналогично обновляем fullName, email
            if (updated) {
                return clientRepository.save(existing);
            }
            return existing;
        }

        // Если не найден, создаём нового
        Client newClient = Client.builder()
                .telegramId(telegramId)
                .phone(phone)
                .fullName(fullName)
                .email(email)
                .build();
        return clientRepository.save(newClient);

    }

    /**
     * Найти клиента по Telegram ID.
     *
     * @param telegramId ID Telegram пользователя
     * @return Optional с клиентом, если найден
     */
    public Optional<Client> findByTelegramId(Long telegramId) {
        List<Client> clients = clientRepository.findByTelegramId(telegramId);
        if (clients.isEmpty()) {
            return Optional.empty();
        }
        if (clients.size() > 1) {
            log.warn("Найдено {} клиентов для telegramId={}. Используется первый.", clients.size(), telegramId);
        }
        return Optional.of(clients.getFirst());
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