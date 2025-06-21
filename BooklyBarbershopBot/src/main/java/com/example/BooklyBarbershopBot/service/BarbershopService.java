package com.example.BooklyBarbershopBot.service;

import com.example.BooklyBarbershopBot.entity.Barbershop;
import com.example.BooklyBarbershopBot.repository.BarbershopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BarbershopService {

    private final BarbershopRepository repository;

    public Optional<Barbershop> getBySlug(String slug) {
        return repository.findByTelegramSlug(slug);
    }
}
