package com.example.BooklyBarbershopBot.deserializer;

import com.example.BooklyBarbershopBot.dto.ImageGroupDto;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ImageGroupDeserializer extends JsonDeserializer<List<ImageGroupDto>> {

    @Override
    public List<ImageGroupDto> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {

        ObjectMapper mapper = (ObjectMapper) p.getCodec();
        List<ImageGroupDto> list = new ArrayList<>();
        JsonToken token = p.currentToken();

        if (token == JsonToken.START_ARRAY) {
            // Если массив — читаем как список
            list = mapper.readValue(p, mapper.getTypeFactory().constructCollectionType(List.class, ImageGroupDto.class));
        } else {
            //например null
            return null;
        }
        return list;
    }
}
