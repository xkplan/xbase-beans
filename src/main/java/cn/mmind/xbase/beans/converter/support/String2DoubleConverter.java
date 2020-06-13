package cn.mmind.xbase.beans.converter.support;

import cn.mmind.xbase.beans.converter.Converter;
import cn.mmind.xbase.beans.utils.NumberUtil;

public class String2DoubleConverter implements Converter<String, Double> {
    @Override
    public boolean canConvert(Class<?> sourceType, Class<?> targetType) {
        return sourceType == String.class && (targetType == double.class || targetType == Double.class);
    }

    @Override
    public Double convert(String value) {
        if (value == null) return null;
        return NumberUtil.parseNumber(value, Double.class);
    }
}
