package cn.mmind.xbase.beans;

import java.util.Map;

public class BeanDefinition {
    private String clazz;
    private String name;
    private BeanScope scope = BeanScope.SINGLETON;
    private Map<String, String> props;
    private Map<String, String> referenceProps;
    private String postConstruct;

    public String getClazz() {
        return clazz;
    }

    public void setClazz(String clazz) {
        this.clazz = clazz;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BeanScope getScope() {
        return scope;
    }

    public void setScope(BeanScope scope) {
        this.scope = scope;
    }

    public Map<String, String> getProps() {
        return props;
    }

    public void setProps(Map<String, String> props) {
        this.props = props;
    }

    public Map<String, String> getReferenceProps() {
        return referenceProps;
    }

    public void setReferenceProps(Map<String, String> referenceProps) {
        this.referenceProps = referenceProps;
    }

    public String getPostConstruct() {
        return postConstruct;
    }

    public void setPostConstruct(String postConstruct) {
        this.postConstruct = postConstruct;
    }
}
