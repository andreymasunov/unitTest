package de.home24.middleware.octestframework.util;

import java.util.Iterator;
import java.util.Properties;

import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

/**
 * Utility class for accessing properties.
 * 
 * @author svb
 *
 */
public class PropertyUtils {

    /**
     * Get the property value that corresponds to the given key;
     * 
     * @param pProperties
     *            the properties
     * @param pKey
     *            the key to search for
     * @return the property value; throws {@link IllegalStateException} if
     *         property could not be found
     */
    public static String getProperty(Properties pProperties, String pKey) {

	String value = (String) pProperties.get(pKey);

	if (value == null) {
	    throw new IllegalStateException(String.format("Property with key %s is not existing!", pKey));
	}

	return value;
    }

    /**
     * Copies all properties from {@link Environment} to a {@link Properties}
     * instance.
     * 
     * @param pEnvironment
     *            the {@link Environment} source to copy from
     * @return the populated {@link Properties} source
     */
    public static Properties environmentToProperties(Environment pEnvironment) {

	final Properties properties = new Properties();

	for (Iterator<PropertySource<?>> it = ((AbstractEnvironment) pEnvironment).getPropertySources()
		.iterator(); it.hasNext();) {
	    PropertySource<?> propertySource = (PropertySource<?>) it.next();
	    if (propertySource instanceof MapPropertySource) {
		properties.putAll(((MapPropertySource) propertySource).getSource());
	    }
	}

	return properties;
    }
}
