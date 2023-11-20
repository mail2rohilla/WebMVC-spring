package com.paytm.acquirer.netc.db.converter;

import com.paytm.acquirer.netc.enums.NetcEndpoint;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

@Converter
public class NetcEndpointConverter implements AttributeConverter<NetcEndpoint, Integer> {

    @Override
    public Integer convertToDatabaseColumn(NetcEndpoint attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.getValue();
    }

    @Override
    public NetcEndpoint convertToEntityAttribute(Integer dbData) {
        if (dbData == null) {
            return null;
        }
        return NetcEndpoint.fromValue(dbData);
    }
}