package cn.mmind.xbase.beans.converter.support;

import cn.mmind.xbase.beans.converter.Converter;
import cn.mmind.xbase.beans.converter.ConverterService;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;

public class DefaultConverterService implements ConverterService {
    private LinkedHashSet<Converter> converters = new LinkedHashSet<>();
    private Map<String, Converter> convertersCache = new HashMap<>();

    @Override
    public void addConverter(Converter<?, ?> converter) {
        if (converter != null) converters.add(converter);
    }

    @Override
    public boolean canConvert(Class<?> sourceType, Class<?> targetType) {
        if (sourceType == null || targetType == null) return false;
        Converter converter = getConverter(sourceType, targetType);
        return converter != null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T convert(Object source, Class<T> targetType) {
        if (source == null || targetType == null) return null;
        Converter<?, T> converter = getConverter(source.getClass(), targetType);
        if (converter == null) return null;
        try {
            return (T) converter.getClass().getDeclaredMethod("convert", Object.class).invoke(converter, source);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private <S, T> Converter<S, T> getConverter(Class<S> sourceType, Class<T> targetType) {
        String cacheKey = sourceType.getName() + "/" + targetType.getName();
        Converter converter = convertersCache.get(cacheKey);
        if (converter != null) return converter;

        for (Converter c : converters) {
            if (c.canConvert(sourceType, targetType)) {
                convertersCache.put(cacheKey, c);
                return c;
            }
        }
        return null;
    }
}
