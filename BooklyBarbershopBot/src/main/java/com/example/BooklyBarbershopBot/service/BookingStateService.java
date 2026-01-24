package com.example.BooklyBarbershopBot.service;

import com.example.BooklyBarbershopBot.dto.BookingData;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Сервис для управления временным состоянием процесса бронирования.
 * <p>
 * Хранит промежуточные данные выбора пользователя (мастер, услуги, время)
 * до момента финального подтверждения записи и её сохранения в базу данных.
 */
@Service
public class BookingStateService {

    /**
     * Потокобезопасная карта для хранения данных бронирования.
     * Ключ — chatId пользователя, значение — объект с накопленными данными сессии.
     */
    private final Map<Long, BookingData> cache = new ConcurrentHashMap<>();

    /**
     * Удаляет данные сессии пользователя.
     * Рекомендуется вызывать после успешного завершения бронирования
     * или при принудительном сбросе процесса.
     *
     * @param chatId идентификатор чата пользователя.
     */
    public void remove(Long chatId) {
        cache.remove(chatId);
    }

    /**
     * Возвращает текущие данные бронирования для пользователя.
     *
     * @param chatId идентификатор чата.
     * @return {@link BookingData} или null, если сессия не начата.
     */
    public BookingData get(Long chatId) {
        return cache.get(chatId);
    }

    /**
     * Сохраняет или обновляет объект данных бронирования.
     *
     * @param chatId идентификатор чата.
     * @param data   объект данных.
     */
    public void put(Long chatId, BookingData data) {
        cache.put(chatId, data);
    }

    /**
     * Возвращает существующие данные сессии или создает новый пустой объект,
     * если пользователь обратился впервые.
     *
     * @param chatId идентификатор чата.
     * @return действующий объект {@link BookingData}.
     */
    public BookingData getOrCreate(Long chatId) {
        return cache.computeIfAbsent(chatId, id -> new BookingData());
    }

}