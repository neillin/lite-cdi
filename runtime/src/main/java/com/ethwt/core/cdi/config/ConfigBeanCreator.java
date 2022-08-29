package com.ethwt.core.cdi.config;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Optional;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.InjectionPoint;


import com.ethwt.core.cdi.annotation.ConfigValue;
import com.networknt.config.Config;
import com.networknt.config.JsonMapper;

import io.quarkus.arc.BeanCreator;
import io.quarkus.arc.impl.InjectionPointProvider;

public class ConfigBeanCreator implements BeanCreator<Object> {

    @Override
    public Object create(CreationalContext<Object> creationalContext, Map<String, Object> params) {
        String requiredType = params.get("requiredType").toString();
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = ConfigBeanCreator.class.getClassLoader();
        }
        Class<?> clazz;
        try {
            clazz = Class.forName(requiredType, true, cl);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Cannot load required type: " + requiredType);
        }

        InjectionPoint injectionPoint = InjectionPointProvider.get();
        if (injectionPoint == null) {
            throw new IllegalStateException("No current injection point found");
        }

        ConfigValue configValue = getConfigValue(injectionPoint);
        if (configValue == null) {
            throw new IllegalStateException("@ConfigValue not found");
        }

        String key = configValue.name();
        String defaultValue = configValue.defaultValue();

        if (defaultValue.isEmpty() || ConfigValue.UNCONFIGURED_VALUE.equals(defaultValue)) {
            return Config.getInstance().getJsonObjectConfig(key, clazz);
        } else {
            Optional<?> value = Optional.ofNullable(Config.getInstance().getJsonObjectConfig(key, clazz));
            if (value.isPresent()) {
                return value.get();
            } else {
                return JsonMapper.fromJson(defaultValue, clazz);
            }
        }
    }


    private ConfigValue getConfigValue(InjectionPoint injectionPoint) {
        for (Annotation qualifier : injectionPoint.getQualifiers()) {
            if (qualifier.annotationType().equals(ConfigValue.class)) {
                return (ConfigValue) qualifier;
            }
        }
        return null;
    }

}
