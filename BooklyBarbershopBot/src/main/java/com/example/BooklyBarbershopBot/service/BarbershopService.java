package com.example.BooklyBarbershopBot.service;

import com.example.BooklyBarbershopBot.entity.Barbershop;
import com.example.BooklyBarbershopBot.repository.BarbershopRepository;
import lombok.RequiredArgsConstructor;
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
public class BarbershopService {
    /**
     * Репозиторий для доступа к данным барбершопов.
     */
    private final BarbershopRepository repository;

    /**
     * Ищет барбершоп по уникальному Telegram slug.
     *
     * @param slug уникальный идентификатор (slug) барбершопа
     * @return {@link Optional} с найденным {@link Barbershop} или пустой, если барбершоп не найден
     */
    public Optional<Barbershop> getBySlug(String slug) {
        return repository.findByTelegramSlug(slug);
    }
}
