package de.home24.middleware.octestframework.util;

/**
 * Utility class for replacing parameters in XML files. The parameter is
 * supposed to be defined in the form ${PARAMETER_NAME}.
 * 
 * @author svb
 *
 */
public class ParameterReplacer {

    private String originalString;

    public ParameterReplacer(String pOriginalString) {

	originalString = pOriginalString;
    }

    /**
     * Replaces all occurrences of the specified parameter.
     * 
     * @param pParametername
     *            the name of the parameter to be replaced
     * @param pReplacementString
     *            the string to be set instead of the parameter name
     * @return a reference of itself
     */
    public ParameterReplacer replace(String pParametername, String pReplacementString) {

	originalString = originalString.replaceAll(String.format("\\$\\{%s\\}", pParametername),
		pReplacementString);
	return this;
    }

    /**
     * Returns the string containing all replacements.
     * 
     * @return the adjusted string
     */
    public String build() {

	return originalString;
    }

}
