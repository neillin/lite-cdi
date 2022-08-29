/**
 * 
 */
package com.ethwt.core.cdi.config.deployment;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Type;
import org.jboss.jandex.Type.Kind;

import com.ethwt.core.cdi.annotation.ConfigValue;
import com.ethwt.core.cdi.config.ConfigBeanCreator;

import io.quarkus.arc.processor.BeanRegistrar;
import io.quarkus.arc.processor.BuildExtension;
import io.quarkus.arc.processor.DotNames;
import io.quarkus.arc.processor.InjectionPointInfo;

/**
 * @author neillin
 *
 */
public class ConfigBeanRegistar implements BeanRegistrar {

	private static final DotName CONFIG_VALUE_NAME = DotName.createSimple(ConfigValue.class.getName());
    private static final DotName MAP_NAME = DotName.createSimple(Map.class.getName());
    private static final DotName SET_NAME = DotName.createSimple(Set.class.getName());
    private static final DotName LIST_NAME = DotName.createSimple(List.class.getName());
    private static final DotName SUPPLIER_NAME = DotName.createSimple(Supplier.class.getName());
	
	@Override
	public void register(RegistrationContext context) {
		registerCustomConfigBeanTypes(context);

	}
	
	void registerCustomConfigBeanTypes(RegistrationContext context) {
		
		Collection<InjectionPointInfo> injectionPointInfos = context.get(BuildExtension.Key.INJECTION_POINTS);

        Set<Type> customBeanTypes = new HashSet<>();

        for (InjectionPointInfo injectionPoint : injectionPointInfos) {
            if (injectionPoint.hasDefaultedQualifier()) {
                // Defaulted qualifier means no @ConfigValue
                continue;
            }

            AnnotationInstance configValue = injectionPoint.getRequiredQualifier(CONFIG_VALUE_NAME);
            if (configValue != null) {
                // Register a custom bean for injection points that are not handled by ConfigProducer
                Type injectedType = injectionPoint.getRequiredType();
                if (!isHandledByProducers(injectedType)) {
                    customBeanTypes.add(injectedType);
                }
            }
        }

        for (Type type : customBeanTypes) {
            DotName implClazz = type.kind() == Kind.ARRAY ? DotName.createSimple(ConfigBeanCreator.class.getName())
                    : type.name();
            context.configure(implClazz)
                    .creator(ConfigBeanCreator.class)
                    .providerType(type)
                    .types(type)
                    .addQualifier(CONFIG_VALUE_NAME)
                    .param("requiredType", type.name().toString()).done();
        }
    }
	
    public static boolean isHandledByProducers(Type type) {
        if (type.kind() == Kind.ARRAY) {
            return false;
        }
        if (type.kind() == Kind.PRIMITIVE) {
            return true;
        }
        return DotNames.STRING.equals(type.name()) ||
                DotNames.OPTIONAL.equals(type.name()) ||
                DotNames.OPTIONAL_INT.equals(type.name()) ||
                DotNames.OPTIONAL_LONG.equals(type.name()) ||
                DotNames.OPTIONAL_DOUBLE.equals(type.name()) ||
                MAP_NAME.equals(type.name()) ||
                SET_NAME.equals(type.name()) ||
                LIST_NAME.equals(type.name()) ||
                DotNames.LONG.equals(type.name()) ||
                DotNames.FLOAT.equals(type.name()) ||
                DotNames.INTEGER.equals(type.name()) ||
                DotNames.BOOLEAN.equals(type.name()) ||
                DotNames.DOUBLE.equals(type.name()) ||
                DotNames.SHORT.equals(type.name()) ||
                DotNames.BYTE.equals(type.name()) ||
                DotNames.CHARACTER.equals(type.name()) ||
                SUPPLIER_NAME.equals(type.name());
    }

}
