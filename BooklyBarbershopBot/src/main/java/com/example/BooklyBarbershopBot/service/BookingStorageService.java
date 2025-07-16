package com.example.BooklyBarbershopBot.service;

import com.example.BooklyBarbershopBot.dto.RecordInfo;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BookingStorageService {

    private final Map<Long, RecordInfo> recordCache = new ConcurrentHashMap<>();

    public void save(Long chatId, Long recordId, String recordHash) {
        recordCache.put(chatId, new RecordInfo(recordId, recordHash));
    }

    public RecordInfo get(Long chatId) {
        return recordCache.get(chatId);
    }

    public void remove(Long chatId) {
        recordCache.remove(chatId);
    }
}
