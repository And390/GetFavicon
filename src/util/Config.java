package util;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;


public class Config {

    public static final String FILE_NAME = "config.properties";

    public static final String encoding = "UTF-8";

    private static Properties properties;

    public static synchronized void load() throws IOException {
        try (FileInputStream input = new FileInputStream(FILE_NAME)) {
            properties = new Properties();
            properties.load(input);
            //    исправить кодировку
            for (Map.Entry<Object, Object> property : properties.entrySet())
                property.setValue(new String(property.getValue().toString().getBytes("ISO-8859-1"), encoding));
        }
    }

    public static synchronized Properties getProperties() {
        if (properties == null) {
            try {
                load();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return properties;
    }

    public static String getOpt(String name) {
        return getProperties().getProperty(name);
    }

    public static String get(String name) {
        String value = getOpt(name);
        if (value == null)  throw new ConfigException(FILE_NAME + ": Missing property " + name);
        return value;
    }

    public static String getNotEmpty(String name) {
        String value = get(name);
        if (value.length() == 0)  throw new ConfigException(FILE_NAME + ": Empty value for " + name);
        return value;
    }

    public static boolean getBool(String name, boolean defaultValue) {
        String value = getOpt(name);
        if (isEmpty(value))  return defaultValue;
        value = value.toLowerCase();
        if (value.equals("true") || value.equals("yes") || value.equals("on"))  return true;
        else if (value.equals("false") || value.equals("no") || value.equals("off"))  return false;
        else  throw new ConfigException(FILE_NAME + ": Missing property " + name);
    }

    public static int getInt(String name, int minValue, int maxValue, String error) throws ConfigException
    {
        String value = getNotEmpty(name);
        try  {  int result = Integer.parseInt(value);
                if (result<minValue || result>maxValue)  throw new NumberFormatException();
                return result;  }
        catch (NumberFormatException e)  {  throw new ConfigException(FILE_NAME + ": "+name+" value "+error+": "+value);  }
    }

    public static int getInt(String name, int minValue, int maxValue) throws ConfigException {
        return getInt(name, minValue, maxValue, "must be integer in range ["+minValue+".."+maxValue+"]");
    }

    public static int getInt(String name) throws ConfigException {
        return getInt(name, Integer.MIN_VALUE, Integer.MAX_VALUE, "must be integer");
    }

    public static int getUInt(String name) throws ConfigException {
        return getInt(name, 0, Integer.MAX_VALUE, "must be non-negative integer");
    }

    public static int getUInt(String name, int limit) throws ConfigException {
        return getInt(name, 0, limit-1);
    }

    public static int getPInt(String name) throws ConfigException {
        return getInt(name, 1, Integer.MAX_VALUE, "must be positive integer");
    }

    public static boolean isEmpty(String value)  {  if (value==null)  return true;  return value.length()==0;  }
}
