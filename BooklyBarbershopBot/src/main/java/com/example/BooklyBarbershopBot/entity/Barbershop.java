package com.example.BooklyBarbershopBot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Сущность {@code Barbershop}, представляющая барбершоп в системе.
 * <p>
 * Эта сущность хранится в базе данных и содержит основную информацию о барбершопе:
 * его уникальный идентификатор, уникальный телеграм-слиг (slug), название,
 * приветственное сообщение и ссылку на логотип.
 * <p>
 * Поле {@code telegramSlug} используется для идентификации барбершопа
 * при запуске Telegram-бота с командой <code>/start</code>, например <code>/start=slug</code>.
 * <p>
 * Использует JPA-аннотации для маппинга на таблицу базы данных и Lombok для генерации
 * геттеров, сеттеров, конструкторов и билдера.
 *
 * @see UUID уникальный идентификатор сущности
 */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Barbershop {

    /**
     * Уникальный идентификатор барбершопа, генерируется автоматически.
     */
    @Id
    @GeneratedValue
    private UUID id;

    /**
     * Уникальный slug (идентификатор) барбершопа для Telegram-бота.
     * Используется в команде /start для однозначного выбора барбершопа.
     * Поле обязательно для заполнения и уникально.
     */
    @Column(unique = true, nullable = false)
    private String telegramSlug; // значение из /start=slug

    /**
     * Название барбершопа.
     */
    private String name;

    /**
     * Приветственное сообщение барбершопа, которое отправляется пользователю при старте.
     * Может содержать до 1000 символов.
     */
    @Column(length = 1000)
    private String greeting;

    /**
     * Ссылка на логотип барбершопа.
     */
    private String logoUrl;

    @Column(name = "yclients_company_id", nullable = false)
    private String yclientsCompanyId;

    @Column(columnDefinition = "TEXT")
    private String aboutText;

    @Column(length = 500)
    private String reviewsUrl;


}
