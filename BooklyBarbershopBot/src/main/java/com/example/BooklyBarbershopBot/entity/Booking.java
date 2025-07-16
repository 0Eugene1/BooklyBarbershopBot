package com.example.BooklyBarbershopBot.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String datetime;

    private String staffName;
    private String serviceName;

    private String slug;

    @ManyToOne
    private Client client;

    private Long recordId;      // ID записи в Yclients
    private String recordHash;  // Hash для управления записью

    private String status;      // PENDING, CONFIRMED, CANCELLED

    private Long staffId;
    private Long serviceId;
}
