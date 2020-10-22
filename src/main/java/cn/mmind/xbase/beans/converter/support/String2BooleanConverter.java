package cn.mmind.xbase.beans.converter.support;

import cn.mmind.xbase.beans.converter.Converter;

import java.util.HashSet;
import java.util.Set;

public class String2BooleanConverter implements Converter<String, Boolean> {
    private static final Set<String> trueValues = new HashSet<>(4);

    private static final Set<String> falseValues = new HashSet<>(4);

    static {
        trueValues.add("true");
        trueValues.add("on");
        trueValues.add("yes");
        trueValues.add("1");

        falseValues.add("false");
        falseValues.add("off");
        falseValues.add("no");
        falseValues.add("0");
    }

    @Override
    public boolean canConvert(Class<?> sourceType, Class<?> targetType) {
        return sourceType == String.class && (targetType == boolean.class || targetType == Boolean.class);
    }

    @Override
    public Boolean convert(String value) {
        value = value.trim().toLowerCase();
        if (value.isEmpty()) return null;
        if (trueValues.contains(value)) {
            return Boolean.TRUE;
        } else if (falseValues.contains(value)) {
            return Boolean.FALSE;
        } else {
            throw new IllegalArgumentException("Invalid boolean value '" + value + "'");
        }
    }
}
