package cn.mmind.xbase.beans;

import cn.mmind.xbase.beans.converter.ConverterService;
import cn.mmind.xbase.beans.converter.support.DefaultConverterService;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 暂不支持 prototype 类型的 bean
 */
public class BeanFactory {
    private static final Object lock = new Object();
    private static final String BEANS_FILE_NAME = "beans.yml";
    private static volatile YamlConfiguration configuration;
    Map<String, Object> earlySingletonBeans = new ConcurrentHashMap<>();
    Map<String, Object> singletonBeans = new ConcurrentHashMap<>();
    Properties properties = new Properties();
    Map<String, BeanDefinition> definitions = new HashMap<>();
    //    private static BeanFactory instance = new BeanFactory();
    ConverterService converterService;

    protected BeanFactory() {
        converterService = new DefaultConverterService();
        //获取运行目录
        String path = BeanFactory.class.getProtectionDomain().getCodeSource().getLocation().getFile();
        File jarFile;
        try {
            jarFile = new File(URLDecoder.decode(path, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        File file = new File(jarFile.getParentFile(), BEANS_FILE_NAME);
        if (!file.exists()) {
            //配置文件不存在
            try (InputStream is = BeanFactory.class.getResourceAsStream(File.separator + BEANS_FILE_NAME)) {
                try (OutputStream os = new FileOutputStream(file);
                     InputStream tmp = new ByteArrayInputStream("beans:\n  ".getBytes())) {
                    byte[] buf = new byte[1024];
                    int len;
                    if (is != null) {
                        //如果内置配置文件存在则保存到磁盘
                        while ((len = is.read(buf)) != -1) os.write(buf, 0, len);
                    } else {
                        //否则创建空配置文件
                        while ((len = tmp.read(buf)) != -1) os.write(buf, 0, len);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        try {
            loadExtJar(file.getParentFile());
            loadBeanProperties(new File(file.getParentFile(), BEANS_FILE_NAME));
            initialSingletonBeans();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 加载磁盘配置文件<br>
     * 将 singleton 的 bean 初始化（未赋值）一份<br>
     * 存入 earlySingletonBeans
     */
    private void loadBeanProperties(File file) {
        if (configuration == null) {
            synchronized (lock) {
                if (configuration == null) {
                    try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                        configuration = YamlConfiguration.loadConfiguration(reader);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        YamlConfiguration config = configuration;
        ConfigurationSection beansSection = config.getConfigurationSection("cn/mmind/xbase/beans");
        if (beansSection != null) {
            for (String beanName : beansSection.getKeys(false)) {
                BeanDefinition bd = new BeanDefinition();
                String sClazz = beansSection.getString(beanName + ".class");
                String scope = beansSection.getString(beanName + ".scope", "SINGLETON");
                assert scope != null;
                bd.setName(beanName);
                bd.setClazz(sClazz);
                bd.setScope(BeanScope.valueOf(scope.toUpperCase()));
                try {
                    Map<String, String> props = new HashMap<>();
                    Map<String, String> referenceProps = new HashMap<>();
                    ConfigurationSection propertySection = beansSection.getConfigurationSection(beanName + ".property");
                    if (propertySection != null) {
                        for (String prop : propertySection.getKeys(false)) {
                            if (propertySection.isString(prop)) {
                                // 省略值写法
                                String value = propertySection.getString(prop);
                                props.put(prop, value);
                            } else {
                                if (propertySection.contains(prop + ".value")) {
                                    String value = propertySection.getString(prop + ".value");
                                    props.put(prop, value);
                                } else if (propertySection.contains(prop + ".value")) {
                                    String ref = propertySection.getString(prop + ".ref");
                                    referenceProps.put(prop, ref);
                                }
                            }
                        }
                    }
                    bd.setProps(props);
                    bd.setReferenceProps(referenceProps);
                    if (bd.getScope() == BeanScope.SINGLETON) {
                        Object bean = constructBean(sClazz);
                        earlySingletonBeans.put(bd.getName(), bean);
                    }
                    /*if (definitions.containsKey(bd.getName()))
                        throw new IllegalStateException("Bean [" + bd.getName() + "] 已存在！");
                    definitions.put(beanName, bd);*/
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 创建一个 Bean 空对象
     *
     * @param sClazz 全类名
     */
    protected Object constructBean(String sClazz) throws ReflectiveOperationException {
        Class<?> clazz = Class.forName(sClazz);
        Constructor<?> constructor = clazz.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    /**
     * 创建一个 Bean 空对象
     *
     * @param aClazz 类
     */
    protected Object constructBean(Class<?> aClazz) throws ReflectiveOperationException {
        Constructor<?> constructor = aClazz.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    /**
     * 初始化 Bean，为单例 Bean 注入依赖
     */
    protected void initialSingletonBeans() throws ReflectiveOperationException {
        for (Map.Entry<String, Object> e : earlySingletonBeans.entrySet()) {
            Object bean = e.getValue();
            assignBean(e.getKey(), bean);
            singletonBeans.put(e.getKey(), bean);
        }
    }

    /**
     * 为 Bean 对象属性赋值注入
     *
     * @param name Bean标识
     * @param bean 对象
     */
    private void assignBean(String name, Object bean) throws ReflectiveOperationException {
        /*BeanDefinition bd = definitions.get(name);
        if (bd == null) throw new NullPointerException("找不到 Bean 对象: " + name);
        for (Map.Entry<String, String> e : bd.getProps().entrySet()) {
            doSetter(bean, e.getKey(), e.getValue());
        }
        for (Map.Entry<String, String> e : bd.getReferenceProps().entrySet()) {
            BeanDefinition ref = definitions.get(e.getValue());
            if (ref == null) throw new NullPointerException(e.getKey() + " 找不到依赖 Bean 对象: " + e.getValue());
            doSetter(bean, e.getKey(), ref);
        }*/
    }

    /**
     * 加载lib文件夹下的jar文件
     */
    private void loadExtJar(File folder) {
        File file = new File(folder, "lib");
        if (!file.exists()) {
            if (!file.mkdirs()) throw new RuntimeException("文件夹创建失败");
            return;
        }
        try {
            URLClassLoader loader = (URLClassLoader) ClassLoader.getSystemClassLoader();
            Method addURL = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            addURL.setAccessible(true);
            File[] files = file.listFiles();
            if (files == null) throw new RuntimeException("拓展库文件夹读取失败");
            for (File f : files) {
                addURL.invoke(loader, f.toURI().toURL());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 解析属性的 setter 方法名
     *
     * @param prop 属性名
     */
    private String resolveSetterName(String prop) {
        StringBuilder kb = new StringBuilder(prop);
        if (kb.charAt(0) >= 'a' && kb.charAt(0) <= 'z')
            kb.setCharAt(0, (char) (kb.charAt(0) - 32));
        kb.insert(0, "set");
        return kb.toString();
    }

    /**
     * 解析方法
     *
     * @param bean       对象
     * @param methodName 方法名
     */
    private Method resolveMethod(Object bean, String methodName) {
        Method setter = null;
        for (Method m : bean.getClass().getMethods()) {
            if (m.getName().equals(methodName)) {
                setter = m;
                break;
            }
        }
        if (setter == null)
            throw new RuntimeException("找不到" + bean.getClass().getName() + "的 setter 方法：" + methodName);
        return setter;
    }

    /**
     * 为 Bean 赋值
     *
     * @param bean  对象
     * @param prop  属性名
     * @param value 属性值
     */
    private void doSetter(Object bean, String prop, Object value) throws InvocationTargetException, IllegalAccessException {
        String methodName = resolveSetterName(prop);
        Method setter = resolveMethod(bean, methodName);
        if (value instanceof String) {
            value = converterService.convert(value, setter.getParameterTypes()[0]);
        }
        setter.invoke(bean, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getBean(String name) {
        /*BeanDefinition bd = definitions.get(name);
        if (bd == null) throw new IllegalStateException("找不到名为[" + name + "]的Bean！");
        if (bd.getScope() == BeanScope.SINGLETON) {
            return (T) singletonBeans.get(name);
        } else if (bd.getScope() == BeanScope.PROTOTYPE) {
            try {
                Object bean = constructBean(bd.getClazz());
                assignBean(bd.getName(), bean);
                return (T) bean;
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }*/
        if (singletonBeans.containsKey(name)) {
            return (T) singletonBeans.get(name);
        }
        throw new IllegalStateException("找不到名为[" + name + "]的Bean！");
    }

    @SuppressWarnings("unchecked")
    public <T> T getBean(Class<T> clazz) {
        List<T> list = new ArrayList<>(4);
        singletonBeans.forEach((name, b) -> {
            if (clazz.isInstance(b)) list.add((T) b);
        });
        if (list.size() == 0) {
            /*ClassLoader classLoader = getClass().getClassLoader();
            for (Map.Entry<String, BeanDefinition> bde : definitions.entrySet()) {
                try {
                    BeanDefinition bd = bde.getValue();
                    Class<?> aClass = classLoader.loadClass(bd.getClazz());
                    if (aClass.isAssignableFrom(clazz)) {
                        Object bean = constructBean(bd.getClazz());
                        assignBean(bd.getName(), bean);
                        list.add((T) bean);
                    }
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                }
            }
            if (list.size() == 0)*/
            throw new IllegalStateException("找不到类型为[" + clazz.getName() + "]的Bean！");
        }
        if (list.size() != 1) throw new IllegalStateException("类型冲突，存在多个类型为[" + clazz.getName() + "]的Bean！");
        return list.get(0);
    }

    @SuppressWarnings("unchecked")
    public <T> Map<String, T> getBeansOfType(Class<T> clazz) {
        Map<String, T> map = new HashMap<>();
        singletonBeans.forEach((name, obj) -> {
            if (clazz.isInstance(obj)) map.put(name, (T) obj);
        });
        return map;
    }
}
