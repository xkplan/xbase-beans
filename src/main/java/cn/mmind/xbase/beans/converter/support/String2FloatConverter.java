package cn.mmind.xbase.beans.converter.support;

import cn.mmind.xbase.beans.converter.Converter;
import cn.mmind.xbase.beans.utils.NumberUtil;

public class String2FloatConverter implements Converter<String, Float> {
    @Override
    public boolean canConvert(Class<?> sourceType, Class<?> targetType) {
        return sourceType == String.class && (targetType == float.class || targetType == Float.class);
    }

    @Override
    public Float convert(String value) {
        if (value == null) return null;
        return NumberUtil.parseNumber(value, Float.class);
    }
}
