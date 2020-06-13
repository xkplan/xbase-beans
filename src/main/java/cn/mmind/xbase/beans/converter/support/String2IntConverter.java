package cn.mmind.xbase.beans.converter.support;

import cn.mmind.xbase.beans.converter.Converter;
import cn.mmind.xbase.beans.utils.NumberUtil;

public class String2IntConverter implements Converter<String, Integer> {
    @Override
    public boolean canConvert(Class<?> sourceType, Class<?> targetType) {
        return sourceType == String.class && (targetType == int.class || targetType == Integer.class);
    }

    @Override
    public Integer convert(String value) {
        if (value == null) return null;
        return NumberUtil.parseNumber(value, Integer.class);
    }
}
