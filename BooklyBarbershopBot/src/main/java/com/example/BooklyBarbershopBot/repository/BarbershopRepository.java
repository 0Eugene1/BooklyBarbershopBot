package com.example.BooklyBarbershopBot.repository;

import com.example.BooklyBarbershopBot.entity.Barbershop;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Репозиторий для работы с сущностью {@link Barbershop}.
 * <p>
 * Расширяет {@link JpaRepository} для предоставления стандартных CRUD операций
 * и содержит дополнительный метод для поиска барбершопа по уникальному Telegram slug.
 * <p>
 * Используется для доступа к данным барбершопов в базе данных.
 */
public interface BarbershopRepository extends JpaRepository<Barbershop, UUID> {

    /**
     * Находит барбершоп по уникальному Telegram slug.
     *
     * @param findByTelegramSlug уникальный идентификатор (slug) барбершопа, используемый в Telegram-боте
     * @return {@link Optional} с найденным {@link Barbershop} или пустой, если барбершоп с таким slug не найден
     */
    Optional<Barbershop> findByTelegramSlug(String findByTelegramSlug);
}
