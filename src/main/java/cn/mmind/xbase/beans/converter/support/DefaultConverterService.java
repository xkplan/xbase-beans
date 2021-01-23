package cn.mmind.xbase.beans.converter.support;

import cn.mmind.xbase.beans.converter.Converter;
import cn.mmind.xbase.beans.converter.ConverterService;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;

public class DefaultConverterService implements ConverterService {
    private LinkedHashSet<Converter> converters = new LinkedHashSet<>();
    private Map<String, Converter> convertersCache = new HashMap<>();

    public DefaultConverterService() {
        addConverter(new String2BooleanConverter());
        addConverter(new String2ByteConverter());
        addConverter(new String2ShortConverter());
        addConverter(new String2IntConverter());
        addConverter(new String2LongConverter());
        addConverter(new String2FloatConverter());
        addConverter(new String2DoubleConverter());
    }

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
            final Method method = converter.getClass().getDeclaredMethod("convert", Object.class);
            method.setAccessible(true);
            return (T) method.invoke(converter, source);
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
