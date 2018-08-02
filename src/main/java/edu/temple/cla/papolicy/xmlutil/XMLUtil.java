package edu.temple.cla.papolicy.xmlutil;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.ClassUtils;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Utility class to manipulate XML files. Provides some of the capabilities of
 * DOM4J and JDOM, but uses only the libraries provided by the JDK.
 *
 * @author Paul Wolfgang
 */
public class XMLUtil {

    /**
     * Method to read an XML element into an object. The element is assumed to
     * be one of the following:
     * <dl>
     * <dt>Primitive type</dt>
     * <dd>The element content is a string that will be parsed into the
     * primitive.</dd>
     * <dt>An object with a single String constructor</dt>
     * <dd>The element content is a string that will be passed to the
     * constructor</dd>
     * <dt>A Set</dt>
     * <dd>The element content is a sequence of child elements which correspond
     * to the generic type of the set. This element is skipped and null is returned.</dd>
     * <dt>An Object with fields</dt>
     * <dd>There is assumed to be a one-to-one correspondence between the
     * contained elements and the fields in the object being read. The class
     * must also have a no-argument constructor.</dd>
     * Empty elements are returned as null or zero.
     *
     * @param <T> the type of the returned object
     * @param clazz the class object
     * @param e The element
     * @return The object that corresponds to the element (primitive types will
     * be wrapped)
     */
    public static <T> T readElement(Class<T> clazz, Element e) {
        Object result = null;
        try {
            if (clazz.isPrimitive()) {
                result = readPrimitive(clazz, e);
            } else if (!hasChildElements(e)) {
                Constructor c = clazz.getDeclaredConstructor(String.class);
                String text = e.getTextContent().trim();
                if (!text.isEmpty()) {
                    result = c.newInstance(text);
                }
            } else if (Set.class.isAssignableFrom(clazz)) {
                result = null;
            } else {
                result = clazz.newInstance();
                for (Element child : getChildElements(e)) {
                    Field field = clazz.getDeclaredField(child.getNodeName());
                    field.setAccessible(true);
                    Class childType = field.getType();
                    field.set(result, readElement(childType, child));
                }
            }
        } catch (InstantiationException |
                IllegalAccessException |
                NoSuchFieldException |
                NoSuchMethodException |
                InvocationTargetException ex) {
            throw new RuntimeException(ex);
        }
        return (T) result;
    }

    /**
     * Method to read the contents of an element as a primitive.
     *
     * @param childType the type of the child element
     * @param e The element
     * @return The value as the corresponding wrapper type
     * @throws NoSuchMethodException
     * @throws IllegalAccessException
     * @throws InstantiationException
     * @throws InvocationTargetException
     */
    public static Object readPrimitive(Class childType, Element e)
            throws NoSuchMethodException, IllegalAccessException,
            InstantiationException, InvocationTargetException {
        Object result = null;
        String text = e.getTextContent().trim();
        if (!text.isEmpty()) {
            Class wrapper = ClassUtils.primitiveToWrapper(childType);
            Constructor c = wrapper.getDeclaredConstructor(String.class);
            result = c.newInstance(text);
        }
        return result;
    }

    /**
     * Method to get the first named child of an element
     *
     * @param e The element to be searched
     * @param name The name of the element
     * @return The element with the desired name or null if not found
     */
    public static Element getChildElement(Element e, String name) {
        Element result = null;
        for (Node child = e.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE && name.equals(child.getNodeName())) {
                return (Element) child;
            }
        }
        return result;
    }

    /**
     * Method to get a list of child elements. Other child nodes are skipped.
     *
     * @param e The element to be searched
     * @return A list of child elements
     */
    public static List<Element> getChildElements(Element e) {
        List<Element> result = new ArrayList<>();
        for (Node child = e.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                result.add((Element) child);
            }
        }
        return result;
    }

    /**
     * Method to get a list of child elements with a given name. Other child
     * nodes are skipped
     *
     * @param e The element to be searched
     * @param name The name to be searched for
     * @return A list of child elements
     */
    public static List<Element> getChildElements(Element e, String name) {
        List<Element> result = new ArrayList<>();
        for (Node child = e.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE && name.equals(child.getNodeName())) {
                result.add((Element) child);
            }
        }
        return result;
    }

    /**
     * Method to determine if an element has child elements.
     *
     * @param e The element to be searched
     * @return true if there is at least one child element
     */
    public static boolean hasChildElements(Element e) {
        for (Node child = e.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                return true;
            }
        }
        return false;
    }

}
