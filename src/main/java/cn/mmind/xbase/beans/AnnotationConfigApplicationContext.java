package cn.mmind.xbase.beans;

import cn.mmind.xbase.beans.utils.ReflectUtil;
import cn.mmind.xbase.core.annotation.Autowired;
import cn.mmind.xbase.core.annotation.Component;
import cn.mmind.xbase.core.annotation.Scope;
import cn.mmind.xbase.core.annotation.Value;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class AnnotationConfigApplicationContext extends BeanFactory {
    public AnnotationConfigApplicationContext(Class<?> clazz) {
        this(clazz, null);
    }

    public AnnotationConfigApplicationContext(Class<?> clazz, File configFolder) {
        try {
            //加载配置文件
            if (configFolder != null && configFolder.exists()) {
                File configFile = new File(configFolder, "config.properties");
                if (configFile.exists()) {
                    try (InputStream is = new FileInputStream(configFile);
                         InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                        properties.load(isr);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            earlySingletonBeans.put("context", this);
            singletonBeans.put("context", this);
            //创建原始对象
            Set<Class<?>> classes = ReflectUtil.getClasses(clazz.getPackage().getName());
            classes.forEach(aClass -> {
                if (aClass.isAnnotation()) return;
                if (aClass.isAnnotationPresent(Component.class)/* || aClass.isAnnotationPresent(Service.class)*/) {
                    try {
                        Scope scope = aClass.getAnnotation(Scope.class);
                        Component component = aClass.getAnnotation(Component.class);
                        if (scope == null || "singleton".equalsIgnoreCase(scope.scopeName())) {
                            Object bean = constructBean(aClass);
                            String name;
                            if (component.value().isEmpty()) {
                                name = aClass.getSimpleName();
                                name = name.substring(0, 1).toLowerCase() + name.substring(1);
                            } else name = component.value();
                            earlySingletonBeans.put(name, bean);
                            BeanDefinition bd = new BeanDefinition();
                            bd.setName(name);
                            bd.setClazz(aClass.getName());
                            bd.setScope(BeanScope.SINGLETON);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
            //注入数据
            earlySingletonBeans.forEach((name, obj) -> {
                try {
                    for (Field field : obj.getClass().getFields()) {
                        if (field.isAnnotationPresent(Autowired.class)) {
                            //注解注入
                            Autowired at = field.getAnnotation(Autowired.class);
                            Object bean;
                            if (at.name().isEmpty()) {
                                bean = getEarlyBean(field.getName());
                            } else bean = getEarlyBean(at.name());
                            if (bean == null) {
                                if (Collection.class.isAssignableFrom(field.getType())) {
                                    Type type = field.getGenericType();
                                    if (type instanceof ParameterizedType) {
                                        Map<String, ?> beans = getEarlyBeansOfType((Class<?>) ((ParameterizedType) type).getActualTypeArguments()[0]);
                                        if (Set.class.isAssignableFrom(field.getType())) {
                                            Set<Object> sets = new HashSet<>();
                                            beans.forEach((s, o) -> sets.add(o));
                                            bean = sets;
                                        } else if (List.class.isAssignableFrom(field.getType())) {
                                            List<Object> lists = new ArrayList<>(beans.size());
                                            beans.forEach((s, o) -> lists.add(o));
                                            bean = lists;
                                        }
                                    }
                                } else bean = getEarlyBean(field.getType());
                            }
                            if (bean == null && at.required()) {
                                throw new IllegalStateException("bean不存在，注入失败");
                            }
                            field.setAccessible(true);
                            field.set(obj, bean);
                        } else if (field.isAnnotationPresent(Value.class)) {
                            //注入配置
                            Value at = field.getAnnotation(Value.class);
                            if (!at.required() && !properties.containsKey(at.value())) continue;
                            if (at.required() && !properties.containsKey(at.value()))
                                throw new IllegalStateException("找不到配置 " + at.value());
                            field.setAccessible(true);
                            if (field.getType() == String.class) {
                                field.set(obj, properties.get(at.value()));
                            } else if (converterService.canConvert(String.class, field.getType())) {
                                field.set(obj, converterService.convert(properties.getProperty(at.value()), field.getType()));
                            } else throw new IllegalStateException("value配置无法转换");
                        }
                    }
                    singletonBeans.put(name, obj);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public AnnotationConfigApplicationContext(String basePackages) {
    }

    @SuppressWarnings("unchecked")
    protected <T> T getEarlyBean(String name) {
        return (T) earlySingletonBeans.get(name);
    }

    @SuppressWarnings("unchecked")
    protected <T> T getEarlyBean(Class<T> clazz) {
        List<T> list = new ArrayList<>(4);
        earlySingletonBeans.forEach((name, b) -> {
            if (clazz.isInstance(b)) list.add((T) b);
        });
        if (list.size() != 1) {
            return null;
        } else return list.get(0);
    }

    @SuppressWarnings("unchecked")
    public <T> Map<String, T> getEarlyBeansOfType(Class<T> clazz) {
        Map<String, T> map = new HashMap<>();
        earlySingletonBeans.forEach((name, obj) -> {
            if (clazz.isInstance(obj)) map.put(name, (T) obj);
        });
        return map;
    }
}
