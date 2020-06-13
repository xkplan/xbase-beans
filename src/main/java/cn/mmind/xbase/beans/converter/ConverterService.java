package cn.mmind.xbase.beans.converter;

public interface ConverterService {
    void addConverter(Converter<?, ?> converter);

    boolean canConvert(Class<?> sourceType, Class<?> targetType);

    <T> T convert(Object source, Class<T> targetType);
}
