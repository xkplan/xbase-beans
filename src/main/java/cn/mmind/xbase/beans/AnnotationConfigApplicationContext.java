package cn.mmind.xbase.beans;

import cn.mmind.xbase.beans.utils.ReflectUtil;
import cn.mmind.xbase.core.annotation.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class AnnotationConfigApplicationContext extends BeanFactory {
    private static final AnnotationConfigApplicationContext instance = new AnnotationConfigApplicationContext();

    private AnnotationConfigApplicationContext() {
        earlySingletonBeans.put("context", this);
        singletonBeans.put("context", this);
    }

    public static BeanFactory load(Class<?> clazz, File configFolder) {
        instance.doLoad(clazz, configFolder);
        return instance;
    }

    private void doLoad(Class<?> clazz, File configFolder) {
        try {
            doLoadConfigurationFile(configFolder);
            //读取所有 Class
            Set<Class<?>> classes = ReflectUtil.getClasses(clazz);
            Map<String, BeanDefinition> localDefinitions = new HashMap<>();
            //加载配置类
            doLoadConfiguration(classes.stream().filter(aClass -> aClass.isAnnotationPresent(Configuration.class))
                    .collect(Collectors.toList()), localDefinitions);
            //加载所有 Component，读取 BeanDefinition
            classes.stream().filter(aClass -> aClass.isAnnotationPresent(Component.class) ||
                    aClass.isAnnotationPresent(Service.class)).forEach(aClass -> {
                Scope scope = aClass.getAnnotation(Scope.class);
                String name;
                name = Optional.ofNullable(aClass.getAnnotation(Component.class)).map(Component::value).orElse("");
                if (name.isEmpty())
                    name = Optional.ofNullable(aClass.getAnnotation(Service.class)).map(Service::value).orElse("");
                if (name.isEmpty())
                    name = Optional.of(aClass.getSimpleName()).map(s -> s.substring(0, 1).toLowerCase() + s.substring(1)).get();
                BeanDefinition bd = new BeanDefinition();
                bd.setName(name);
                bd.setClazz(aClass.getName());
                BeanScope beanScope = BeanScope.SINGLETON;
                if (scope != null && !"singleton".equalsIgnoreCase(scope.value())) beanScope = BeanScope.PROTOTYPE;
                bd.setScope(beanScope);
                if (definitions.containsKey(name)) throw new IllegalStateException("已存在名为 " + name + " 的 bean");
                localDefinitions.put(name, bd);
                definitions.put(name, bd);
            });
            //创建原始早期单例对象
            Map<String, Object> localEarlySingletonBeans = new ConcurrentHashMap<>();
            localDefinitions.forEach((s, bd) -> {
                if (bd.getScope() == BeanScope.SINGLETON) {
                    try {
                        Object bean = constructBean(bd.getClazz());
                        localEarlySingletonBeans.put(bd.getName(), bean);
                        earlySingletonBeans.put(bd.getName(), bean);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
            //注入数据
            doInject(localEarlySingletonBeans);
            //执行后置处理器方法
            localDefinitions.forEach((s, bd) -> {
                if (bd.getScope() == BeanScope.SINGLETON) {
                    try {
                        Object bean = singletonBeans.get(bd.getName());
                        Method postMethod = bean.getClass().getDeclaredMethod(bd.getPostConstruct());
                        if (postMethod != null) {
                            postMethod.setAccessible(true);
                            postMethod.invoke(bean);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void doLoadConfigurationFile(File configFolder) {
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
            configFile = new File(configFolder, "config.yml");
            if (configFile.exists()) {
                YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
                Map<String, Object> values = config.getValues(true);
                values.forEach((s, o) -> {
                    if (o instanceof ConfigurationSection) return;
                    properties.put(s, o);
                });
            }
        }
    }

    private void doLoadConfiguration(List<Class<?>> classes, Map<String, BeanDefinition> localDefinitions) {
        classes.forEach(aClass -> {
            if (aClass.isAnnotationPresent(Configuration.class)) {
                try {
                    Object obj = aClass.newInstance();
                    for (Method method : aClass.getDeclaredMethods()) {
                        if (method.isAnnotationPresent(Bean.class)) {
                            //注册一个 bean
                            Bean ab = method.getAnnotation(Bean.class);
                            String name;
                            if (ab.value().isEmpty()) {
                                name = aClass.getSimpleName();
                                name = name.substring(0, 1).toLowerCase() + name.substring(1);
                            } else name = ab.value();
                            Object bean = method.invoke(obj);
                            if (bean != null) {
                                BeanDefinition bd = new BeanDefinition();
                                bd.setName(name);
                                bd.setClazz(bean.getClass().getName());
                                bd.setScope(BeanScope.SINGLETON);
                                if (definitions.containsKey(name)) {
                                    System.out.println("已存在名为 " + name + " 的 bean");
                                    continue;
                                }
                                localDefinitions.put(name, bd);
                                definitions.put(name, bd);
                                earlySingletonBeans.put(name, bean);
                                singletonBeans.put(name, bean);
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void doInject(Map<String, Object> localEarlySingletonBeans) {
        localEarlySingletonBeans.forEach((name, obj) -> {
            for (Field field : obj.getClass().getDeclaredFields()) {
                try {
                    if (field.isAnnotationPresent(Autowired.class)) {
                        //注解注入
                        Autowired autowired = field.getAnnotation(Autowired.class);
                        Object bean;
                        //通过名称注入
                        if (autowired.name().isEmpty()) {
                            bean = getEarlyBean(field.getName());
                        } else bean = getEarlyBean(autowired.name());
                        //如果名称注入找不到则通过类型注入
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
                        //如果还找不到且强制要求注入则报错
                        if (bean == null && autowired.required()) {
                            throw new IllegalStateException("类型为 " + field.getType().getName() + " 的 bean 不存在，注入失败");
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
                            field.set(obj, properties.getProperty(at.value()));
                        } else if (converterService.canConvert(String.class, field.getType())) {
                            field.set(obj, converterService.convert(properties.get(at.value()).toString(), field.getType()));
                        } else {
                            try {
                                field.set(obj, properties.get(at.value()));
                            } catch (IllegalAccessException e) {
                                throw new IllegalStateException("value 配置无法转换");
                            }
                        }
                    }
                    singletonBeans.put(name, obj);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
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
