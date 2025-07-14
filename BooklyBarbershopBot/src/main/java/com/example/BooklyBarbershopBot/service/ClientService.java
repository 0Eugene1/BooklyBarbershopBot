package com.example.BooklyBarbershopBot.service;

import com.example.BooklyBarbershopBot.entity.Client;
import com.example.BooklyBarbershopBot.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ClientService {
    private final ClientRepository clientRepository;

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
}
