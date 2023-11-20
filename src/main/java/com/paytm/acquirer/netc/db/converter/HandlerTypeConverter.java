package com.paytm.acquirer.netc.db.converter;

import com.paytm.acquirer.netc.enums.HandlerType;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

@Converter
public class HandlerTypeConverter implements AttributeConverter<HandlerType, Integer> {

    @Override
    public Integer convertToDatabaseColumn(HandlerType attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.getValue();
    }

    @Override
    public HandlerType convertToEntityAttribute(Integer dbData) {
        if (dbData == null) {
            return null;
        }
        return HandlerType.fromValue(dbData);
    }
}