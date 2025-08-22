package com.example.BooklyBarbershopBot.service;

import com.example.BooklyBarbershopBot.entity.Barbershop;
import com.example.BooklyBarbershopBot.repository.BarbershopRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Сервисный класс для работы с сущностью {@link Barbershop}.
 * <p>
 * Предоставляет методы для получения информации о барбершопах, взаимодействуя
 * с {@link BarbershopRepository} — репозиторием данных.
 * <p>
 * Используется в бизнес-логике приложения для поиска барбершопа по уникальному slug.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BarbershopService {
    /**
     * Репозиторий для доступа к данным барбершопов.
     */
    private final BarbershopRepository barbershopRepository;

    /**
     * Ищет барбершоп по уникальному Telegram slug.
     *
     * @param slug уникальный идентификатор (slug) барбершопа
     * @return {@link Optional} с найденным {@link Barbershop} или пустой, если барбершоп не найден
     */
    public Optional<Barbershop> getBySlug(String slug) {
        log.info("Searching barbershop by slug: {}", slug);
        Optional<Barbershop> barbershop = barbershopRepository.findByTelegramSlug(slug);
        if (barbershop.isEmpty()) {
            log.warn("Barbershop not found for slug: {}", slug);
        } else {
            log.info("Barbershop found: id={}, name={}", barbershop.get().getId(), barbershop.get().getName());
        }
        return barbershop;
    }
}
