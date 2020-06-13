package cn.mmind.xbase.beans.converter;

public interface Converter<S, T> {
    boolean canConvert(Class<?> sourceType, Class<?> targetType);

    T convert(S value);
}
