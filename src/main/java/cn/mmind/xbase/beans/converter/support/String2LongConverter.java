package cn.mmind.xbase.beans.converter.support;

import cn.mmind.xbase.beans.converter.Converter;
import cn.mmind.xbase.beans.utils.NumberUtil;

public class String2LongConverter implements Converter<String, Long> {
    @Override
    public boolean canConvert(Class<?> sourceType, Class<?> targetType) {
        return sourceType == String.class && (targetType == long.class || targetType == Long.class);
    }

    @Override
    public Long convert(String value) {
        if (value == null) return null;
        return NumberUtil.parseNumber(value, Long.class);
    }
}
