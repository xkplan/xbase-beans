package cn.mmind.xbase.beans.converter.support;

import cn.mmind.xbase.beans.converter.Converter;
import cn.mmind.xbase.beans.utils.NumberUtil;

public class String2ShortConverter implements Converter<String, Short> {
    @Override
    public boolean canConvert(Class<?> sourceType, Class<?> targetType) {
        return sourceType == String.class && (targetType == short.class || targetType == Short.class);
    }

    @Override
    public Short convert(String value) {
        if (value == null) return null;
        return NumberUtil.parseNumber(value, Short.class);
    }
}
