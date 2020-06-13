package cn.mmind.xbase.beans.converter.support;

import cn.mmind.xbase.beans.converter.Converter;
import cn.mmind.xbase.beans.utils.NumberUtil;

public class String2ByteConverter implements Converter<String, Byte> {
    @Override
    public boolean canConvert(Class<?> sourceType, Class<?> targetType) {
        return sourceType == String.class && (targetType == byte.class || targetType == Byte.class);
    }

    @Override
    public Byte convert(String value) {
        if (value == null) return null;
        return NumberUtil.parseNumber(value, Byte.class);
    }
}
