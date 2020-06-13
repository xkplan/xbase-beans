package cn.mmind.xbase.beans;

import cn.mmind.xbase.beans.converter.ConverterService;
import cn.mmind.xbase.beans.converter.support.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class BeanFactory {
    private static final String BEANS_FILE_NAME = "beans.yml";
    private Map<String, Object> beans = new HashMap<>();
    private static BeanFactory instance = new BeanFactory();
    private ConverterService converterService;

    private BeanFactory() {
        converterService = new DefaultConverterService();
        converterService.addConverter(new String2BooleanConverter());
        converterService.addConverter(new String2ByteConverter());
        converterService.addConverter(new String2ShortConverter());
        converterService.addConverter(new String2IntConverter());
        converterService.addConverter(new String2LongConverter());
        converterService.addConverter(new String2FloatConverter());
        converterService.addConverter(new String2DoubleConverter());
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
        loadExtJar(file.getParentFile());
        loadBean(file.getParentFile());
    }

    /**
     * 加载磁盘配置文件
     */
    private void loadBean(File folder) {
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(new File(folder, BEANS_FILE_NAME)), StandardCharsets.UTF_8)) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(reader);
            ConfigurationSection beansSection = config.getConfigurationSection("beans");
            if (beansSection != null) {
                for (String beanName : beansSection.getKeys(false)) {
                    String sClazz = beansSection.getString(beanName + ".class");
                    try {
                        Class<?> clazz = Class.forName(sClazz);
                        Constructor<?> constructor = clazz.getDeclaredConstructor();
                        constructor.setAccessible(true);
                        Object bean = constructor.newInstance();
                        ConfigurationSection propertySection = beansSection.getConfigurationSection(beanName + ".property");
                        if (propertySection != null) {
                            for (String prop : propertySection.getKeys(false)) {
                                StringBuilder kb = new StringBuilder(prop);
                                if (kb.charAt(0) >= 'a' && kb.charAt(0) <= 'z')
                                    kb.setCharAt(0, (char) (kb.charAt(0) - 32));
                                kb.insert(0, "set");
                                String methodName = kb.toString();
                                Method setter = null;
                                for (Method m : clazz.getMethods()) {
                                    if (m.getName().equals(methodName)) {
                                        setter = m;
                                        break;
                                    }
                                }
                                if (setter == null) throw new RuntimeException("找不到属性 setter 方法：" + methodName);
                                setter.invoke(bean, converterService.convert(propertySection.getString(prop), setter.getParameterTypes()[0]));
//                                doSetter(setter, bean, propertySection.getString(prop));
                            }
                        }
                        beans.put(beanName, bean);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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

    @SuppressWarnings("unchecked")
    public static <T> T getBean(String name) {
        if (instance.beans.containsKey(name)) return (T) instance.beans.get(name);
        return null;
    }

    @SuppressWarnings("unchecked")
    public static <T> Map<String, T> getBeansOfType(Class<T> clazz) {
        Map<String, T> map = new HashMap<>();
        instance.beans.forEach((name, obj) -> {
            if (clazz.isInstance(obj)) map.put(name, (T) obj);
        });
        return map;
    }
}
