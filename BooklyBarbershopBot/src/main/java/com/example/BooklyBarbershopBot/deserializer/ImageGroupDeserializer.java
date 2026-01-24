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
 * Кастомный десериализатор для обработки поля изображений в формате JSON.
 * <p>
 * Необходим из-за особенностей внешних API, которые могут возвращать изображения
 * в разных форматах: массив объектов {@code [...]}, одиночный объект {@code {...}}
 * или значение {@code null}.
 * <p>
 * Пример использования в DTO:
 * <pre>
 * {@code @JsonDeserialize(using = ImageGroupDeserializer.class)}
 * private List<ImageGroupDto> images;
 * </pre>
 */
public class ImageGroupDeserializer extends JsonDeserializer<List<ImageGroupDto>> {

    /**
     * Преобразует входящий JSON-узел в типизированный список.
     * <p>
     * Логика обработки токенов:
     * <ul>
     * <li>{@link JsonToken#START_ARRAY}: стандартная десериализация коллекции.</li>
     * <li>{@link JsonToken#START_OBJECT}: чтение одного объекта и упаковка в {@link ArrayList}.</li>
     * <li>{@link JsonToken#VALUE_NULL}: возврат неизменяемого пустого списка.</li>
     * </ul>
     *
     * @param p    JsonParser для потокового чтения данных.
     * @param ctxt Контекст десериализации (используется для доступа к конфигурации маппера).
     * @return Гарантированный ненулевой список объектов {@link ImageGroupDto}.
     * @throws IOException при нарушении структуры JSON или проблемах ввода-вывода.
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
