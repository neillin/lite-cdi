package com.ethwt.core.cdi.annotation;

import static java.lang.annotation.ElementType.*;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import javax.enterprise.util.AnnotationLiteral;
import javax.enterprise.util.Nonbinding;
import javax.inject.Qualifier;



@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({METHOD, FIELD, PARAMETER, TYPE})
public @interface ConfigValue {
	
	String UNCONFIGURED_VALUE="com.ethwt.core.cdi.config.configproperty.unconfigureddvalue";
    /**
     * 
     * The name of configure which will be used to load configure with {@code com.networknt.config.Config}
     *
     * @return name of the config
     */
    @Nonbinding
    String name() default "values";

    /**
     * <p>The default value if the configured property value does not exist.
     *
     *
     * @return the default value as a string
     */
    @Nonbinding
    String defaultValue() default UNCONFIGURED_VALUE;
    
    
    /**
     * The key of the config property used to look up the configuration value.
     * If it is not specified and name is "values", it will be derived automatically as {@code <simple_class_name>.<injection_point_name>},
     * where {@code injection_point_name} is the field name or parameter name,
     * {@code simple_class_name} is the simple name of the class being injected to.
     * If one of the {@code simple_class_name} or {@code injection_point_name} cannot be determined, the value has to be provided.
     *
     * @return Name (key) of the config property to inject
     */
    @Nonbinding
    String property() default "";
    
    public static final class Literal extends AnnotationLiteral<ConfigValue> implements ConfigValue {

    	private String name = "values";
    	private String defaultValue = "";
    	private String property = "";
    	
    	
		public Literal() {
			super();
		}
		
		

		public Literal(String name, String defaultValue, String property) {
			super();
			this.name = name;
			this.defaultValue = defaultValue;
			this.property = property;
		}



		@Override
		public String name() {
			return this.name;
		}

		@Override
		public String defaultValue() {
			return this.defaultValue;
		}

		@Override
		public String property() {
			return this.property;
		}

    }

}