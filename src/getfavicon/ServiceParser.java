package getfavicon;

import com.sun.org.apache.xml.internal.security.exceptions.Base64DecodingException;
import com.sun.org.apache.xml.internal.security.utils.Base64;
import org.jsoup.Connection;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import utils.ExternalException;
import utils.Util;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * And390 - 28.07.2017
 */
public class ServiceParser
{
    public static final Map<String, Map<String, Application.ServiceImages>> services = new HashMap<>();

    public static void loadServiceImages() throws IOException, ExternalException, Base64DecodingException
    {
        loadGoogleServices();
        loadYandexServices();
        loadMailServices();
    }


    //        ----    Google    ----

    private static void loadGoogleServices() throws IOException, ExternalException, Base64DecodingException
    {
        Map<String, Application.ServiceImages> serviceImages = new LinkedHashMap<>();
        services.put("google", serviceImages);
        URL allURL = new URL("https://www.google.com/intl/en/about/products/");
        Connection con = Application.getConnection(allURL.toString());
        Document doc = con.get();

        //  No 'all' services icon, add later.

        //
        for (Element item : doc.getElementsByClass("carousel-slide"))
        {
            Element icon = item.getElementsByClass("products-grid-item-icon").first();
            if (icon == null)  continue;
            String iconSrc = getAttr(icon, "data-lazy-src");
            String title = getText(getElementByClassName(item, "products-grid-item-title"));
            Element links = getElementByClassNameOrNull(item, "product-links-list");
            if (links!=null)  for (Element link : links.getElementsByTag("a"))  {
                if (link.text().equals("Use on the web"))  {
                    String href = getAttr(link, "href");
                    URL url = new URL(allURL, href);
                    href = url.getProtocol() + "://" + url.getHost() + url.getPath();

                    //    service found
                    String serviceName = lastWord(title.toLowerCase());
                    if (serviceName==null)  continue;
                    if (serviceName.equals("google+"))  serviceName = "plus";

                    Application.ServiceImages images = loadServiceImage(con, title, href, iconSrc);
                    if (!images.isEmpty())  serviceImages.put(serviceName, images);

                    break;
                }
            }
        }

        if (serviceImages.isEmpty() || serviceImages.get("search")==null)  throw new ExternalException("Can't parse Google services");

        //  add 'all products'
        Application.ServiceImages images = new Application.ServiceImages("Google products", "https://www.google.com/about/products/");
        images.addAll(serviceImages.get("search"));
        serviceImages.put("all", images);
    }

    private static String lastWord(String source)  {
        String[] s = source.split(" +");
        return s.length>0 ? s[s.length-1] : null;
    }


    //        ----    Yandex    ----

    private static void loadYandexServices() throws IOException, ExternalException, Base64DecodingException
    {
        Map<String, Application.ServiceImages> serviceImages = new LinkedHashMap<>();
        services.put("yandex", serviceImages);
        URL allURL = new URL("https://yandex.ru/all");
        Connection con = Application.getConnection(allURL.toString());
        Document doc = con.get();

        //  add 'all' service
        Application.ServiceImages allService = new Application.ServiceImages("Все сервисы Яндекса", allURL.toString());
        Application.loadImages(con, doc, allService);
        if (allService.isEmpty())  throw new ExternalException("Icon for Yandex services page is not found");
        serviceImages.put("all", allService);

        //  css
        Map<String, byte[]> svgData = new HashMap<>();
        Map<String, byte[]> pngData = new HashMap<>();
        String css = con.url("https://yastatic.net/www/_/S/C/_w0OBcze0QDWiaVG7AmNAH1Ms.css").execute().body();
        Pattern cssRulePattern = Pattern.compile("([^\\{\\}]+)\\{([^\\}]*)\\}");
        Matcher cssRules = cssRulePattern.matcher(css);
        while (cssRules.find())  {
            String name = cssRules.group(1).trim();
            if (!name.matches("\\.[^\\s]+"))  continue;
            name = name.substring(1);
            String body = cssRules.group(2).trim();
            String url = Util.cutIfSurroundedOrNull(body, "background-image:url(\"", "\")");
            if (url == null)  url = Util.cutIfSurroundedOrNull(body, "background-image:url(\'", "\')");
            if (url == null)  url = Util.cutIfSurroundedOrNull(body, "background-image:url(", ")");
            String svg = Util.cutIfStartsOrNull(url, "data:image/svg+xml;charset=utf8,");
            if (svg != null)  svgData.put(name, URLDecoder.decode(svg, "utf8").getBytes("UTF8"));
            else  {
                String png = Util.cutIfStartsOrNull(url, "data:image/png;base64,");
                if (png != null)  pngData.put(name, Base64.decode(png));
                else  throw new ExternalException("Unsupported url: " + url);
            }
        }

        //  html - parse two blocks: 'main' and 'all'
        String[] BLOCK_CLASSES = new String[] { "b-line__services-main", "b-line__services-all" };
        String[] ITEM_CLASSES = new String[] { "services-big__item", "services-all__item_wrap" };
        String[] CAPTION_CLASSES = new String[] { "services-big__item_link", "services-all__link" };
        String[] ICON_CLASSES = new String[] { "services-big__item_icon", "services-all__icon" };
        for (int i=0; i<BLOCK_CLASSES.length; i++)  {
            Element main = getElementByClassName(doc, BLOCK_CLASSES[i]);
            for (Element el : main.getElementsByClass(ITEM_CLASSES[i]))  {
                Element a = getFirstElementByTagName(el, "a");
                String href = getAttr(a, "href");
                if (a.children().size() != 1)  throw new ExternalException("Link doesn't countain exactly one child");
                String caption = getText(getElementByClassName(a.child(0), CAPTION_CLASSES[i]));
                Element icon = getElementByClassName(a.child(0), ICON_CLASSES[i]);

                URL url = new URL(allURL, href);
                String host = url.getHost();
                String path = url.getPath();
                href = url.getProtocol() + "://" + host + path;
                String serviceName = getServiceName(url, "yandex.ru", "search");
                if (serviceImages.containsKey(serviceName))  continue;  //service already loaded
                if (EXCLUDE_YANDEX_SERVICES.contains(serviceName))  continue;  //ignore

                Application.ServiceImages images = new Application.ServiceImages(caption, href);
                Application.SiteImageItem item = new Application.SiteImageItem(Application.SiteImageItem.ANY_SIZE, 1, href);
                if (serviceName.equals("rabota"))  Application.loadSvg(YANDEX_RABOTA_SVG.getBytes("UTF8"), images, item);  //predefined
                else  {
                    //  find icon in css
                    boolean found = false;
                    boolean foundSvg = false;
                    for (String className : icon.className().split("\\s+"))  {
                        final String ICON_CLASS_SERVICE_PREFIX = "b-ico-";
                        if (!className.equals(ICON_CLASSES[i]) && className.startsWith(ICON_CLASS_SERVICE_PREFIX))  {
                            if (!svgData.containsKey(className) && !pngData.containsKey(className))  continue;
                            //serviceName = className.substring(ICON_CLASS_SERVICE_PREFIX.length());
                            //serviceName = Util.cutIfEnds(serviceName, "_small");
                            if (svgData.containsKey(className))  {  foundSvg=true;  Application.loadSvg(svgData.get(className), images, item);  }
                            else if (pngData.containsKey(className))  {
                                BufferedImage image = ImageIO.read(new ByteArrayInputStream(pngData.get(className)));
                                if (image==null)  throw new ExternalException("Can't load image for class ."+className);
                                Application.checkAndAddImage(images, image, item);
                            }
                            else  continue;
                            found = true;
                            break;
                        }
                    }
                    if (!found)  throw new ExternalException("Icon class isn't found: " + icon.className());
                    if (!foundSvg)  {
                        Application.loadImages(href, images);
                    }
                }

                if (!images.isEmpty())  serviceImages.put(serviceName, images);
            }
        }
    }

    private static final List<String> EXCLUDE_YANDEX_SERVICES = Arrays.asList("pdd", "site", "browser", "yandexdatafactory");

    private static final String YANDEX_RABOTA_SVG =
            "<svg width='56' height='56' viewBox='0 0 56 56' xmlns='http://www.w3.org/2000/svg' xmlns:xlink='http://www.w3.org/1999/xlink'>\n" +
            "    <g fill='none' fill-rule='evenodd'>\n" +
            "        <path fill='#F33' d='M14.126 17.662l32.101 32.102 5.44-5.441-32.1-32.102z'/>\n" +
            "        <path fill='#000' d='M20.128 5.692L4.332 21.53l7.638 7.755L35.62 5.692z'/>\n" +
            "        <path fill='#FC0' fill-rule='nonzero' d='M46.227 6.236L12.863 39.6l5.441 5.44 33.364-33.363z'/>\n" +
            "        <g transform='translate(7.597 39.426)'>\n" +
            "            <path fill='#EFB30E' d='M5.267.171l5.44 5.444L0 10.882z'/>\n" +
            "            <path fill='#000' d=\"M0 10.882l2.36 -4.81a5.441 5.441 0 0 1 2.51 2.42z\"/>\n" +
            "        </g>\n" +
            "    </g>\n" +
            "</svg>";


    //        ----    Mail    ----

    public static void loadMailServices() throws IOException, ExternalException, Base64DecodingException
    {
        Map<String, Application.ServiceImages> serviceImages = new LinkedHashMap<>();
        services.put("mail", serviceImages);
        URL allURL = new URL("https://mail.ru/all");
        Connection con = Application.getConnection(allURL.toString());
        Document doc = con.get();

        //  add 'all' service
        Application.ServiceImages allService = new Application.ServiceImages("Все проекты Mail.Ru", allURL.toString());
        Application.loadImages(con, doc, allService);
        if (allService.isEmpty())  throw new ExternalException("Icon for MailRu services page is not found");
        serviceImages.put("all", allService);

        Element projects = doc.getElementById("projects");
        //Pattern CSS_RULE_ICON_PATTERN = Pattern.compile("\\s*background\\s*:\\s*url\\((.+)\\).*");
        for (Element item : projects.getElementsByClass("projects__item"))
        {
            String href = getAttr(item, "href");
            URL serviceURL = new URL(allURL, href);
            href = serviceURL.toString();
            String serviceName = getServiceName(serviceURL, "mail.ru", "search");
            if (serviceName.equals("e"))  serviceName = "mail";
            else if (serviceName.equals("go"))  serviceName = "search";
            if (serviceImages.containsKey(serviceName))  throw new ExternalException("Duplication service: " + serviceName);

            getElementByClassName(item, "projects__item__icon");

            String title = getText(getElementByClassName(item, "projects__item__title"));
            //String description = getText(getElementByClassName(item, "projects__item__desc"));

            Application.ServiceImages images = new Application.ServiceImages(title, href);
            Application.loadImages(href, images);
            if (images.isEmpty())  throw new ExternalException("Can't load images for " + href);
            serviceImages.put(serviceName, images);
        }
        if (serviceImages.isEmpty() || serviceImages.get("search")==null)  throw new ExternalException("Can't parse MailRu services");
    }


    //        ----    util    ----

    private static Element getElementByClassNameOrNull(Element parent, String className) throws ExternalException
    {
        Elements els = parent.getElementsByClass(className);
        if (els.isEmpty())  return null;
        if (els.size() != 1)  throw new ExternalException("Multiple elements found by class name: " + className);
        return els.get(0);
    }

    private static Element getElementByClassName(Element parent, String className) throws ExternalException
    {
        Element result = getElementByClassNameOrNull(parent, className);
        if (result == null)  throw new ExternalException("No elements found by class name '" + className + "' for " + parent);
        return result;
    }

    private static Element getElementByTagName(Element parent, String tagName) throws ExternalException
    {
        Elements els = parent.getElementsByTag(tagName);
        if (els.isEmpty())  throw new ExternalException("No elements found by tag name: " + tagName);
        if (els.size() != 1)  throw new ExternalException("Multiple elements found by tag name: " + tagName);
        return els.get(0);
    }

    private static Element getFirstElementByTagName(Element parent, String tagName) throws ExternalException
    {
        Elements els = parent.getElementsByTag(tagName);
        if (els.isEmpty())  throw new ExternalException("No elements found by tag name: " + tagName);
        return els.get(0);
    }

    private static String getAttr(Element element, String attr) throws ExternalException
    {
        String value = element.attr(attr);
        if (Util.isEmpty(value))  throw new ExternalException("No attribute value: " + attr);
        return value;
    }

    private static String getText(Element element) throws ExternalException
    {
        String text = element.text();
        if (Util.isEmpty(text))   throw new ExternalException("No text for " + element);
        return text;
    }


    private static Application.ServiceImages loadServiceImage(Connection con, String title, String href, String iconUrl)
    {
        Application.ServiceImages images = new Application.ServiceImages(title, href);
        Application.SiteImageItem image = new Application.SiteImageItem(Application.SiteImageItem.UNKNOWN, 1, iconUrl);
        Application.loadImage(con, images, image);
        return images;
    }

    private static String getServiceName(URL url, String serviceBaseUrl, String serviceBaseName)
    {
        String host = url.getHost();
        String path = url.getPath();
        path = Util.cutIfStarts(path, "/");
        return host.equals(serviceBaseUrl) ? Util.isEmpty(path) ? serviceBaseName : Util.sliceBefore(path, '/') :
               host.endsWith("."+serviceBaseUrl) ? Util.cutIfEnds(host, "."+serviceBaseUrl) :
               Util.sliceBeforeLast(host, '.');
    }
}
