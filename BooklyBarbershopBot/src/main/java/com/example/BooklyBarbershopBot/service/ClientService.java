package com.example.BooklyBarbershopBot.service;

import com.example.BooklyBarbershopBot.entity.Client;
import com.example.BooklyBarbershopBot.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Сервис для управления профилями клиентов.
 * <p>
 * Обеспечивает регистрацию новых пользователей, актуализацию их контактных данных
 * и отслеживание контекста посещений (последний использованный филиал).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ClientService {
    private final ClientRepository clientRepository;

    /**
     * Создает или обновляет профиль клиента на основе предоставленных данных.
     * <p>
     * Реализует логику "склейки" аккаунтов: если клиент найден по Telegram ID или телефону,
     * его профиль дополняется новыми данными (имя, email).
     *
     * @param phone      номер телефона (основной идентификатор в CRM).
     * @param telegramId уникальный ID пользователя в Telegram.
     * @param fullName   имя и фамилия.
     * @param email      адрес электронной почты.
     * @return актуальный объект {@link Client}.
     */
    public Client saveOrGetClient(String phone, Long telegramId, String fullName, String email) {
        // Шаг 1: Поиск по Telegram ID
        Optional<Client> existingByTelegramId = findByTelegramId(telegramId);
        if (existingByTelegramId.isPresent()) {
            Client existing = existingByTelegramId.get();
            boolean updated = updateClientFields(existing, phone, fullName, email);
            return updated ? clientRepository.save(existing) : existing;
        }

        // Шаг 2: Поиск по номеру телефона (если по Telegram ID не нашли)
        Optional<Client> existingByPhone = clientRepository.findByPhone(phone);
        if (existingByPhone.isPresent()) {
            Client existing = existingByPhone.get();
            existing.setTelegramId(telegramId); // Привязываем Telegram к существующему телефону
            updateClientFields(existing, null, fullName, email);
            return clientRepository.save(existing);
        }

        // Шаг 3: Создание нового профиля
        log.info("Регистрация нового клиента: phone={}, telegramId={}", phone, telegramId);
        Client newClient = Client.builder()
                .telegramId(telegramId)
                .phone(phone)
                .fullName(fullName)
                .email(email)
                .build();
        return clientRepository.save(newClient);
    }

    /**
     * Вспомогательный метод для обновления полей клиента (если они не заполнены или изменились).
     */
    private boolean updateClientFields(Client client, String phone, String fullName, String email) {
        boolean updated = false;
        if (fullName != null && !fullName.equals(client.getFullName())) {
            client.setFullName(fullName);
            updated = true;
        }
        if (phone != null && !phone.equals(client.getPhone())) {
            client.setPhone(phone);
            updated = true;
        }
        if (email != null && !email.equals(client.getEmail())) {
            client.setEmail(email);
            updated = true;
        }
        return updated;
    }

    /**
     * Находит клиента по Telegram ID.
     */
    public Optional<Client> findByTelegramId(Long telegramId) {
        List<Client> clients = clientRepository.findByTelegramId(telegramId);
        if (clients.isEmpty()) return Optional.empty();

        if (clients.size() > 1) {
            log.warn("Обнаружено дублирование профилей для telegramId={}", telegramId);
        }
        return Optional.of(clients.get(0));
    }

    /**
     * Сохраняет последний активный филиал (slug), с которым взаимодействовал пользователь.
     */
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