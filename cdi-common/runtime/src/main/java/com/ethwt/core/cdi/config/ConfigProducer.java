package com.ethwt.core.cdi.config;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Supplier;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Dependent;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.AnnotatedMember;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.InjectionPoint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ethwt.core.cdi.annotation.ConfigValue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.config.Config;
import com.networknt.config.JsonMapper;


/**
 * CDI producer for {@link Config}.
 *
 * @author Neil Lin
 */
@ApplicationScoped
public class ConfigProducer {
	
	private static final Logger log = LoggerFactory.getLogger(ConfigProducer.class);
	
	private Map<String,Map<String, JsonNode>> configRoots = new HashMap<>();
	
	private synchronized Map<String, JsonNode> getConfigJson(String name) {
		return configRoots.computeIfAbsent(name, key -> {
			ObjectMapper mapper = Config.getInstance().getMapper();
			Map<String, Object> map = Config.getInstance().getJsonMapConfigNoCache(name);
			if(map != null && !map.isEmpty()) {
				try {
					return flatMap(mapper.readTree(JsonMapper.toJson(map)), null, null);
				} catch(IOException e) {
					log.error("Failed to load values config", e);
				}
			} 
			return Collections.emptyMap();
		});
	}
	
	
	private static Map<String, JsonNode> flatMap(JsonNode root, String parent, Map<String, JsonNode> map) {
		if (map == null) {
			map = new HashMap<>();
		}
		final Map<String, JsonNode> result = map;
		root.fields().forEachRemaining(entry -> {
			String key = Optional.ofNullable(parent).map(p -> p+"."+entry.getKey()).orElse(entry.getKey());
			result.put(key, entry.getValue());
			flatMap(entry.getValue(), key, result);
		});
		return map;
	}
	
    private static String getDefaultValue(InjectionPoint injectionPoint) {
        for (Annotation qualifier : injectionPoint.getQualifiers()) {
            if (qualifier.annotationType().equals(ConfigValue.class)) {
                String str = ((ConfigValue) qualifier).defaultValue().trim();
                if (!ConfigValue.UNCONFIGURED_VALUE.equals(str)) {
                    return str;
                }
                Class<?> rawType = rawTypeOf(injectionPoint.getType());
                if (rawType.isPrimitive()) {
                    if (rawType == char.class) {
                        return null;
                    } else if (rawType == boolean.class) {
                        return "false";
                    } else {
                        return "0";
                    }
                }
                return null;
            }
        }
        return null;
    }
    
    private static <T> T convertValue(JsonNode json, Class<T> requiredType) {
    	if (json == null||json.isMissingNode()||json.isNull()) {
    		return null;
    	}
    	Object result = null;
    
    	if(requiredType == String.class) {
    		result = json.asText();
    	} else if(requiredType == Boolean.class|| requiredType == Boolean.TYPE) {
        	result = json.asBoolean();
        } else if(requiredType == Integer.class || requiredType == Integer.TYPE) {
        	result = json.numberValue().intValue();
        } else if(requiredType == Long.class || requiredType == Long.TYPE) {
        	result = json.numberValue().longValue();
        } else if(requiredType == Float.class || requiredType == Float.TYPE) {
        	result = json.numberValue().floatValue();
        } else if(requiredType == Double.class || requiredType == Double.TYPE) {
        	result = json.numberValue().doubleValue();
        } else if( requiredType == Short.class || requiredType == Short.TYPE) {
        	result = json.numberValue().shortValue();
        } else if(requiredType == Byte.class || requiredType == Byte.TYPE) {
        	result = json.numberValue().byteValue();
        } else if(requiredType == Character.class || requiredType == Character.TYPE) {
        	result = json.asText().charAt(0);
        } else {
        	try {
				result = JsonMapper.objectMapper.readValue(json.asText(), requiredType);
			} catch (JsonProcessingException e) {
				log.error("Failed to convert config value of type: {} from string: {}, returning NULL", requiredType, json, e);
				result = null;
			}
        }
    	return requiredType.cast(result);
    }
    
    private static String getName(InjectionPoint injectionPoint) {
        for (Annotation qualifier : injectionPoint.getQualifiers()) {
            if (qualifier.annotationType().equals(ConfigValue.class)) {
                ConfigValue configValue = ((ConfigValue) qualifier);
                return configValue.name().trim();
            }
        }
        return null;
    }
    
    private static String getPropertyPath(InjectionPoint injectionPoint) {
        for (Annotation qualifier : injectionPoint.getQualifiers()) {
            if (qualifier.annotationType().equals(ConfigValue.class)) {
                ConfigValue configValue = ((ConfigValue) qualifier);
                return getPropertyPath(injectionPoint, configValue);
            }
        }
        return null;
    }
    
    static String getPropertyPath(InjectionPoint ip, ConfigValue configValue) {
        String key = configValue.property().trim();
        if (!key.isEmpty()) {
            return key;
        }
        if ("values".equals(configValue.name()) && ip.getAnnotated() instanceof AnnotatedMember) {
            AnnotatedMember<?> member = (AnnotatedMember<?>) ip.getAnnotated();
            AnnotatedType<?> declaringType = member.getDeclaringType();
            if (declaringType != null) {
                StringBuilder sb = new StringBuilder(declaringType.getJavaClass().getSimpleName()).append('.').append(member.getJavaMember().getName());
                return sb.toString();
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T> Class<T> rawTypeOf(final Type type) {
        if (type instanceof Class<?>) {
            return (Class<T>) type;
        } else if (type instanceof ParameterizedType) {
            return rawTypeOf(((ParameterizedType) type).getRawType());
        } else if (type instanceof GenericArrayType) {
            return (Class<T>) Array.newInstance(rawTypeOf(((GenericArrayType) type).getGenericComponentType()), 0).getClass();
        } else {
            throw new IllegalArgumentException("Type has no raw type class: "+type);
        }
    }

    
    private JsonNode getJsonValue(String name, String path, String defaultValue) {
    	JsonNode json = getConfigJson(name).get(path);
        if(json.isMissingNode()||json.isNull()) {
        	try {
				json = defaultValue != null ? JsonMapper.objectMapper.readTree(defaultValue) : null;
			} catch (JsonProcessingException e) {
				log.error("Failed to parse default value: {} ", defaultValue, e);
				throw new IllegalArgumentException("Invalid default value: "+defaultValue);
			}
        }
        return json;
    }
    
    @SuppressWarnings("unchecked")
	public <T> T getValue(String name, String path, Type type, String defaultValue) {
        if (name == null) {
            return null;
        }
        JsonNode json = getJsonValue(name, path, defaultValue);
        if(json == null||json.isMissingNode()||json.isNull()) {
        	return null;
        }
        Class<?> rawType = rawTypeOf(type);
        if(rawType == List.class||rawType == Collection.class) {
        	Class<Object> actualType = (Class<Object>)((ParameterizedType)type).getActualTypeArguments()[0];
        	List<Object> result = new ArrayList<>();
        	json.forEach(n -> result.add(convertValue(n, actualType)));
        	return (T)result;
        } else if(rawType == Set.class) {
        	Class<Object> actualType = (Class<Object>)((ParameterizedType)type).getActualTypeArguments()[0];
        	Set<Object> result = new HashSet<>();
        	json.forEach(n -> result.add(convertValue(n, actualType)));
        	return (T)result;
        } else if(rawType == Map.class) {
        	Class<Object> actualType = (Class<Object>)((ParameterizedType)type).getActualTypeArguments()[1];
        	Map<String,Object> map = new HashMap<>();
        	json.fields().forEachRemaining(entry -> map.put(entry.getKey(), convertValue(entry.getValue(), actualType)));
        	return (T)map;
        } else if(rawType == Optional.class){
        	Class<Object> actualType = (Class<Object>)((ParameterizedType)type).getActualTypeArguments()[0];
        	return (T)Optional.ofNullable(convertValue(json, actualType));
        } else if(rawType == Supplier.class){
        	Class<Object> actualType = (Class<Object>)((ParameterizedType)type).getActualTypeArguments()[0];
        	Supplier<?> val = () -> convertValue(json, actualType);
        	return (T)val;
        } else if(rawType == OptionalInt.class){
        	Integer val = convertValue(json, Integer.class);
        	return val != null ? (T)OptionalInt.of(val) : (T)OptionalInt.empty();
        } else if(rawType == OptionalLong.class){
        	Long val = convertValue(json, Long.class);
        	return val != null ? (T)OptionalLong.of(val) : (T)OptionalLong.empty();
        } else if(rawType == OptionalDouble.class){
        	Double val = convertValue(json, Double.class);
        	return val != null ? (T)OptionalDouble.of(val) : (T)OptionalDouble.empty();
        } else if(path == null) {
        	return (T)Config.getInstance().getJsonObjectConfig(name, rawType);
        } else {
        	return (T)convertValue(json, rawType);
        }
        
    }

    @Dependent
    @Produces
    @ConfigValue
    public String produceStringConfigValue(InjectionPoint ip) {
        return getValue(getName(ip), getPropertyPath(ip), String.class, getDefaultValue(ip));
    }

    @Dependent
    @Produces
    @ConfigValue
    public Long getLongValue(InjectionPoint ip) {
    	return getValue(getName(ip),  getPropertyPath(ip), Long.class, getDefaultValue(ip));
    }

    @Dependent
    @Produces
    @ConfigValue
    public Integer getIntegerValue(InjectionPoint ip) {
    	return getValue(getName(ip),  getPropertyPath(ip), Integer.class, getDefaultValue(ip));
    }

    @Dependent
    @Produces
    @ConfigValue
    public Float produceFloatConfigValue(InjectionPoint ip) {
    	return getValue(getName(ip),  getPropertyPath(ip), Float.class, getDefaultValue(ip));
    }

    @Dependent
    @Produces
    @ConfigValue
    public Double produceDoubleConfigValue(InjectionPoint ip) {
    	return getValue(getName(ip),  getPropertyPath(ip), Double.class, getDefaultValue(ip));
    }

    @Dependent
    @Produces
    @ConfigValue
    public Boolean produceBooleanConfigValue(InjectionPoint ip) {
    	return getValue(getName(ip),  getPropertyPath(ip), Boolean.class, getDefaultValue(ip));
    }

    @Dependent
    @Produces
    @ConfigValue
    public Short produceShortConfigValue(InjectionPoint ip) {
    	return getValue(getName(ip),  getPropertyPath(ip), Short.class, getDefaultValue(ip));
    }

    @Dependent
    @Produces
    @ConfigValue
    public Byte produceByteConfigValue(InjectionPoint ip) {
    	return getValue(getName(ip),  getPropertyPath(ip), Byte.class, getDefaultValue(ip));
    }

    @Dependent
    @Produces
    @ConfigValue
    public Character produceCharacterConfigValue(InjectionPoint ip) {
    	return getValue(getName(ip),  getPropertyPath(ip), Character.class, getDefaultValue(ip));
    }

    @Dependent
    @Produces
    @ConfigValue
    public <T> Optional<T> produceOptionalConfigValue(InjectionPoint ip) {
    	return getValue(getName(ip),  getPropertyPath(ip), ip.getType(), getDefaultValue(ip));
    }

    @Dependent
    @Produces
    @ConfigValue
    public <T> Supplier<T> produceSupplierConfigValue(InjectionPoint ip) {
    	return getValue(getName(ip),  getPropertyPath(ip), ip.getType(), getDefaultValue(ip));
    }

    @Dependent
    @Produces
    @ConfigValue
    public <T> Set<T> producesSetConfigValue(InjectionPoint ip) {
    	return getValue(getName(ip),  getPropertyPath(ip), ip.getType(), getDefaultValue(ip));
    }

    @Dependent
    @Produces
    @ConfigValue
    public <T> List<T> producesListConfigValue(InjectionPoint ip) {
    	return getValue(getName(ip),  getPropertyPath(ip), ip.getType(), getDefaultValue(ip));
    }

    @Dependent
    @Produces
    @ConfigValue
    public <V> Map<String, V> producesMapConfigValue(InjectionPoint ip) {
    	return getValue(getName(ip),  getPropertyPath(ip), ip.getType(), getDefaultValue(ip));
    }

    @Dependent
    @Produces
    @ConfigValue
    public OptionalInt produceOptionalIntConfigValue(InjectionPoint ip) {
    	return getValue(getName(ip),  getPropertyPath(ip), ip.getType(), getDefaultValue(ip));
    }

    @Dependent
    @Produces
    @ConfigValue
    public OptionalLong produceOptionalLongConfigValue(InjectionPoint ip) {
    	return getValue(getName(ip),  getPropertyPath(ip), ip.getType(), getDefaultValue(ip));
    }

    @Dependent
    @Produces
    @ConfigValue
    public OptionalDouble produceOptionalDoubleConfigValue(InjectionPoint ip) {
    	return getValue(getName(ip),  getPropertyPath(ip), ip.getType(), getDefaultValue(ip));
    }
    
    public <T> T produceConfigObject(InjectionPoint ip) {
    	return getValue(getName(ip),  getPropertyPath(ip), ip.getType(), getDefaultValue(ip));
    }
    
    

}