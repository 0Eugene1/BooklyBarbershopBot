package com.example.BooklyBarbershopBot.deserializer;

import com.example.BooklyBarbershopBot.dto.ImageGroupDto;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Кастомный десериализатор для JSON-данных, представляющих список объектов ImageGroupDto.
 * <p>
 * Позволяет десериализовать как массив JSON-объектов, так и одиночный JSON-объект,
 * преобразуя его в список из одного элемента.
 * Если значение null, возвращает пустой список.
 */
public class ImageGroupDeserializer extends JsonDeserializer<List<ImageGroupDto>> {

    /**
     * Десериализует JSON в список ImageGroupDto.
     * Поддерживает варианты:
     * - JSON-массив объектов
     * - Один JSON-объект (оборачивается в список из одного элемента)
     * - null (возвращает пустой список)
     *
     * @param p JsonParser для чтения JSON
     * @param ctxt контекст десериализации
     * @return список ImageGroupDto
     * @throws IOException в случае ошибок чтения JSON
     */
    @Override
    public List<ImageGroupDto> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {

        ObjectMapper mapper = (ObjectMapper) p.getCodec();
        JsonToken token = p.currentToken();
        List<ImageGroupDto> list = new ArrayList<>();

        if (token == JsonToken.START_ARRAY) {
            // Случай: []
            list = mapper.readValue(p, mapper.getTypeFactory().constructCollectionType(List.class, ImageGroupDto.class));
        } else if (token == JsonToken.START_OBJECT) {
            // Случай: { ... } → оборачиваем в список
            ImageGroupDto obj = mapper.readValue(p, ImageGroupDto.class);
            list.add(obj);
        } else if (token == JsonToken.VALUE_NULL) {
            // Если null
            return Collections.emptyList();
        }
        return list;
    }
}
