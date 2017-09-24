package getfavicon;

import com.kitfox.svg.SVGException;
import org.jsoup.Connection;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import util.Config;
import utils.ExternalException;
import utils.Util;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * And390 - 28.07.2017
 */
@SuppressWarnings("unused")
public class ServiceParser
{
    public static final Map<String, Map<String, Application.ServiceImages>> services = new LinkedHashMap<>();
    public static final Map<String, String> providerNames = new LinkedHashMap<>();
    public static final Map<String, Application.ServiceImages> otherServices = new LinkedHashMap<>();
    public static final Map<String, Application.ServiceImages> servicesByDomain = new LinkedHashMap<>();

    public static void loadServiceImages() throws IOException, ExternalException, SVGException
    {
        if (!Config.getBool("test", false)) {
            loadGoogleServices();
            loadYahooServices();
            loadYandexServices();
            loadMailServices();
            loadOtherServices();
            makeServicesByDomain();
        }
    }


    //        ----    Google    ----

    private static void loadGoogleServices() throws IOException, ExternalException
    {
        Map<String, Application.ServiceImages> serviceImages = new LinkedHashMap<>();
        services.put("google", serviceImages);
        providerNames.put("google", "Google");
        URL allURL = new URL("https://www.google.com/intl/en/about/products/");
        Connection con = Application.getConnection(allURL.toString());
        Document doc = con.get();

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
        Application.ServiceImages images = new Application.ServiceImages("All products", "https://www.google.com/about/products/");
        images.addAll(serviceImages.get("search"));
        serviceImages.put("all", images);
    }

    private static String lastWord(String source)  {
        String[] s = source.split(" +");
        return s.length>0 ? s[s.length-1] : null;
    }


    //        ----    Yandex    ----

    private static void loadYandexServices() throws IOException, ExternalException, SVGException
    {
        Map<String, Application.ServiceImages> serviceImages = new LinkedHashMap<>();
        services.put("yandex", serviceImages);
        providerNames.put("yandex", "Яндекс");
        URL allURL = new URL("https://yandex.ru/all");
        Connection con = Application.getConnection(allURL.toString());
        Document doc = con.get();

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
                if (png != null)  pngData.put(name, Base64.getDecoder().decode(png));
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
                Element a = el.getElementsByTag("a").first();  //getFirstElementByTagNameOrNull(el, "a");
                if (a==null)  continue;
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
                Application.SiteImageItem item = new Application.SiteImageItem(Application.SiteImageItem.UNKNOWN, 1, href);
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

                //    additional services that can be not show (because of location)
                if (serviceName.equals("maps") && !serviceImages.containsKey("metro"))  {
                    images = loadServiceImage(con, "Метро", "https://metro.yandex.ru/", "https://metro.yandex.ru/favicon.svg");
                    //images = loadServiceImages("Метро", "https://metro.yandex.ru/", "metro");
                    if (!images.isEmpty())  serviceImages.put("metro", images);
                }
                else if (serviceName.equals("suvenirka") && !serviceImages.containsKey("taxi"))  {
                    byte[] svg = svgData.get("b-ico-taxi");
                    if (svg != null)  {
                        images = new Application.ServiceImages("Такси", "https://taxi.yandex.ru/");
                        item = new Application.SiteImageItem(Application.SiteImageItem.UNKNOWN, 1, href);
                        Application.loadSvg(svg, images, item);
                        if (!images.isEmpty())  serviceImages.put("taxi", images);
                    }
                }
            }
        }

        //  add 'all' service
        serviceImages.put("all", loadServiceImages(con, doc, "Все сервисы", allURL.toString(), "Yandex services page"));
    }

    private static final List<String> EXCLUDE_YANDEX_SERVICES = Arrays.asList("pdd", "site", "browser", "dns", "yandexdatafactory");

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

    private static void loadMailServices() throws IOException, ExternalException
    {
        Map<String, Application.ServiceImages> serviceImages = new LinkedHashMap<>();
        services.put("mail", serviceImages);
        providerNames.put("mail", "Mail.Ru");
        URL allURL = new URL("https://mail.ru/all");
        Connection con = Application.getConnection(allURL.toString());
        Document doc = con.get();

        //  add home service
        serviceImages.put("home", loadServiceImages(con, doc, "Mail.Ru", "https://mail.ru", "MailRu"));

        Element projects = doc.getElementById("projects");
        //Pattern CSS_RULE_ICON_PATTERN = Pattern.compile("\\s*background\\s*:\\s*url\\((.+)\\).*");
        for (Element item : projects.getElementsByClass("projects__item"))
        {
            String href = getAttr(item, "href");
            URL serviceURL = new URL(allURL, href);
            href = serviceURL.toString();
            String serviceName = getServiceName(serviceURL, "mail.ru", "home");
            if (serviceName.equals("e"))  serviceName = "mail";
            else if (serviceName.equals("go"))  serviceName = "search";
            if (serviceImages.containsKey(serviceName))  throw new ExternalException("Duplicated service: " + serviceName);

            getElementByClassName(item, "projects__item__icon");

            String title = getText(getElementByClassName(item, "projects__item__title"));
            //String description = getText(getElementByClassName(item, "projects__item__desc"));

            Application.ServiceImages images = new Application.ServiceImages(title, href);
            Application.loadImages(href, images);
            if (images.isEmpty() && !serviceName.equals("my")   //TODO ban for my.mail.ru
                    )  throw new ExternalException("Can't load images for " + href);
            serviceImages.put(serviceName, images);
        }
        if (serviceImages.isEmpty() || serviceImages.get("search")==null)  throw new ExternalException("Can't parse MailRu services");

        //  add 'all' service
        serviceImages.put("all", loadServiceImages(con, doc, "Все сервисы", allURL.toString(), "MailRu services page"));
    }


    //        ----    Yahoo    ----

    private static void loadYahooServices() throws IOException, ExternalException, SVGException
    {
        Map<String, Application.ServiceImages> serviceImages = new LinkedHashMap<>();
        services.put("yahoo", serviceImages);
        providerNames.put("yahoo", "Yahoo");
        URL allURL = new URL("https://www.yahoo.com/everything/");
        Connection con = Application.getConnection(allURL.toString());
        Document doc = con.get();

        //  single icons set for all
        Application.ServiceImages allImages = loadServiceImages(con, doc, "All services", allURL.toString(), "Yahoo services page");
        replaceSvg(allImages, YAHOO_SVG);

        //  parse
        Element container = getElementByClassName(doc, "Jc(sa)");
        for (Element item : container.getElementsByTag("dd"))
        {
            Element link = getElementByTagName(item, "a");
            String href = getAttr(link, "href");
            URL serviceURL = new URL(allURL, href);
            href = serviceURL.toString();
            String serviceName = getServiceName(serviceURL, "yahoo.com", "home");
            if (serviceName.equals("edit"))  serviceName = "login";
            else if (serviceURL.getHost().equals("sports.yahoo.com") && serviceURL.getPath().startsWith("/fantasy/"))  serviceName = "fantasy";
            else if (serviceURL.getPath().startsWith("/news/weather/"))  serviceName = "weather";
            else if (serviceURL.getPath().startsWith("/news/tagged/autos"))  continue;  //ignore
            if (serviceImages.containsKey(serviceName))  throw new ExternalException("Duplicated service: " + serviceName);
            if (EXCLUDE_YAHOO_SERVICES.contains(serviceName))  continue;  //ignore
            String title = getText(link);

            Application.ServiceImages images;
            switch (serviceName) {
                case "answers":  images = loadServiceImage(con, title, href, "https://s.yimg.com/ge/myc/answersnow/answersnow_icon.png");  break;
                case "mail":  images = loadServiceImage(con, title, href, "https://s.yimg.com/ge/myc/mail_app_icon_0417.png");  break;
                case "sports":  images = loadServiceImage(con, title, href, "https://s.yimg.com/ge/new/Sports_Icon_New.png");  break;
                case "fantasy":  images = loadServiceImage(con, title, href, "https://s.yimg.com/ge/new/Fantasy_ICON.png");  break;
                case "finance":  images = loadServiceImage(con, title, href, "https://s.yimg.com/ge/new/Finance_ICON.png");  break;
                case "news":  images = loadServiceImage(con, title, href, "https://s.yimg.com/ge/myc/newsroom_app_icon_ios.png");  break;
                case "search":  images = loadServiceImage(con, title, href, "https://s.yimg.com/ge/new/SEARCH_ICON.png");  break;
                case "messenger":  images = loadServiceImage(con, title, href, "https://s.yimg.com/ge/new/MESSENGER_ICON.png");  break;
                case "weather":  images = loadServiceImage(con, title, href, "https://s.yimg.com/ge/new/WEATHER_ICON.png");  break;
                case "movies":  images = loadServiceImage(con, title, href, "https://s.yimg.com/ge/new/VIEW_ICON.png");  break;
                case "match":  images = new Application.ServiceImages(title, href);  images.addAll(allImages);;  break;  //TODO match.com is not available from russia
                default:
                    if (serviceURL.getHost().endsWith(".yahoo.com"))  {
                        images = new Application.ServiceImages(title, href);
                        images.addAll(allImages);
                    }
                    else  {
                        images = loadServiceImages(title, href, serviceName);
                    }
            }
            serviceImages.put(serviceName, images);
        }
        if (serviceImages.isEmpty() || serviceImages.get("search")==null)  throw new ExternalException("Can't parse Yahoo services");

        //  add 'all' service
        serviceImages.put("all", allImages);
    }

    private static final List<String> EXCLUDE_YAHOO_SERVICES = Arrays.asList("downloads", "mobile", "toolbar");

    private static final String YAHOO_SVG =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<!-- Generator: Adobe Illustrator 16.0.4, SVG Export Plug-In . SVG Version: 6.00 Build 0)  -->\n" +
            "<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\" \"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\">\n" +
            "<svg version=\"1.1\" id=\"Layer_1\" xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" x=\"0px\" y=\"0px\"\n" +
            "\t width=\"3024px\" height=\"3024px\" viewBox=\"0 0 3024 3024\" enable-background=\"new 0 0 3024 3024\" xml:space=\"preserve\">\n" +
            "<path fill=\"#6001d2\" d=\"M1515.778,205.669C981.54,205.669,476.37,135.396,0-0.008c0,1068.396,0,2756.137,0,3024.016\n" +
            "\tc476.37-135.406,981.54-205.678,1515.778-205.678c528.163,0,1031.723,68.74,1508.222,205.678c0-1028.865,0-1919.085,0-3024.016\n" +
            "\tC2547.501,136.957,2043.941,205.669,1515.778,205.669z M2353.338,463.804l-18.163,28.818\n" +
            "\tc-17.094,27.133-32.388,50.271-53.773,82.474c-28.56,42.729-81.775,127.019-145.562,235.543\n" +
            "\tc-17.617,29.904-39.284,66.361-62.221,104.957c-43.037,72.421-91.819,154.506-129.665,219.889\n" +
            "\tc-15.9,27.728-32.095,55.903-48.402,84.298c-42.299,73.63-86.041,149.764-127.873,223.314\n" +
            "\tc-43.399,76.25-85.883,150.915-128.288,225.84v75.033c0,104.111,2.157,217.242,6.057,318.561\n" +
            "\tc1.859,46.055,3.741,128.156,5.733,215.074c2.375,103.506,4.829,210.529,7.438,264.551l0.779,16.256l0.094,1.971l-17.549-5.006\n" +
            "\tc-6.877-1.961-13.856-3.748-20.918-5.383c-21.498-4.521-44.457-8.213-66.956-10.348c-13.755-1.125-27.735-1.691-41.9-1.691\n" +
            "\tc-0.057,0-0.113,0-0.17,0c-0.056,0-0.104,0-0.165,0c-14.169,0-28.146,0.566-41.904,1.691c-22.488,2.135-45.451,5.826-66.952,10.348\n" +
            "\tc-7.059,1.635-14.037,3.422-20.919,5.383l-17.549,5.006l0.098-1.971l0.779-16.256c2.608-54.021,5.063-161.045,7.435-264.551\n" +
            "\tc1.995-86.918,3.877-169.02,5.729-215.074c3.907-101.318,6.061-214.449,6.061-318.561v-75.033\n" +
            "\tc-42.405-74.926-84.89-149.59-128.292-225.84c-41.829-73.551-85.57-149.685-127.87-223.314\n" +
            "\tc-16.311-28.395-32.497-56.57-48.405-84.298c-37.84-65.383-86.621-147.467-129.663-219.889\n" +
            "\tc-22.94-38.596-44.604-75.053-62.22-104.957c-63.787-108.524-117.003-192.814-145.562-235.543\n" +
            "\tc-21.385-32.204-36.68-55.341-53.777-82.474l-18.159-28.818l-0.117-0.188l32.957,9.44c42.194,12.089,85.16,17.965,131.348,17.965\n" +
            "\tc46.015,0,90.265-5.927,131.51-17.611l9.983-2.829l5.037,9.076c81.625,147.26,300.785,507.46,431.723,722.674\n" +
            "\tc45.156,74.209,80.898,132.967,98.715,162.765c0.061-0.102,0.121-0.207,0.181-0.309c0.061,0.102,0.128,0.208,0.188,0.309\n" +
            "\tc17.812-29.798,53.562-88.556,98.712-162.765c130.941-215.214,350.1-575.415,431.726-722.674l5.029-9.076l9.99,2.829\n" +
            "\tc41.246,11.685,85.491,17.611,131.51,17.611c46.188,0,89.15-5.876,131.345-17.965l32.96-9.44L2353.338,463.804z\"/>\n" +
            "</svg>\n";



    //        ----    Other    ----

    private static void loadOtherServices() throws IOException, ExternalException
    {
        otherServices.put("google", services.get("google").get("search"));
        otherServices.put("yahoo", services.get("yahoo").get("home"));
        otherServices.put("yandex", services.get("yandex").get("search"));
        otherServices.put("mail", services.get("mail").get("home"));

        otherServices.put("bing", loadServiceImage("Bing", "https://www.bing.com/", "https://upload.wikimedia.org/wikipedia/commons/0/07/Bing_favicon.svg"));
        otherServices.put("ebay", loadServiceImage("eBay", "https://www.ebay.com/", "https://upload.wikimedia.org/wikipedia/commons/1/1b/EBay_logo.svg"));
        otherServices.put("amazon", loadServiceImage("Amazon", "https://www.amazon.com/", "https://upload.wikimedia.org/wikipedia/commons/b/b4/Amazon-icon.png"));
    }


    private static void makeServicesByDomain() throws MalformedURLException
    {
        for (Map<String, Application.ServiceImages> providerServices : services.values())
            for (Application.ServiceImages service : providerServices.values()) {
                URL url = new URL(service.url);
                String domain = Util.cutIfStarts(url.getHost(), "www.");
                servicesByDomain.put(domain, service);
            }
        for (Application.ServiceImages service : otherServices.values()) {
            URL url = new URL(service.url);
            String domain = Util.cutIfStarts(url.getHost(), "www.");
            servicesByDomain.put(domain, service);
        }
    }


    //        ----    util    ----

    private static Element getElementByClassNameOrNull(Element parent, String className) throws ExternalException
    {
        Elements els = parent.getElementsByClass(className);
        if (els.isEmpty())  return null;
        if (els.size() != 1)  throw new ExternalException("Multiple elements found by class name '" + className + "' for " + parent);
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
        if (els.isEmpty())  throw new ExternalException("No elements found by tag name '" + tagName + "' for " + parent);
        if (els.size() != 1)  throw new ExternalException("Multiple elements found by tag name '" + tagName + "' for " + parent);
        return els.get(0);
    }

    private static Element getElementByTagAndText(Element parent, String tagName, String text) throws ExternalException
    {
        Elements els = parent.getElementsByTag(tagName);
        if (els.isEmpty())  throw new ExternalException("No elements found by tag name '" + tagName + "' for " + parent);
        Element result = null;
        for (Element el : els)  if (getText(el).equals(text))  {
            if (result != null)  throw new ExternalException("Multiple elements found by tag name '" + tagName + "' and text '" + text + "' for " + parent);
            result = el;
        }
        if (result == null)  throw new ExternalException("No elements found by tag name '" + tagName + "' and text '" + text + "' for " + parent);
        return result;
    }

    private static Element getFirstElementByTagName(Element parent, String tagName) throws ExternalException
    {
        Elements els = parent.getElementsByTag(tagName);
        if (els.isEmpty())  throw new ExternalException("No elements found by tag name '" + tagName + "' for " + parent);
        return els.get(0);
    }

    private static String getAttr(Element element, String attr) throws ExternalException
    {
        String value = element.attr(attr);
        if (Util.isEmpty(value))  throw new ExternalException("No attribute value '" + attr + "' for " + element);
        return value;
    }

    private static String getText(Element element) throws ExternalException
    {
        String text = element.text();
        if (Util.isEmpty(text))   throw new ExternalException("No text for " + element);
        return text;
    }


    private static Application.ServiceImages loadServiceImages(String title, String href, String name) throws IOException, ExternalException
    {
        Application.ServiceImages images = new Application.ServiceImages(title, href);
        Application.loadImages(href, images);
        if (images.isEmpty())  throw new ExternalException("Icon for '"+name+"' is not found");
        return images;
    }

    private static Application.ServiceImages loadServiceImages(Connection con, Document doc, String title, String href, String name) throws IOException, ExternalException
    {
        Application.ServiceImages images = new Application.ServiceImages(title, href);
        Application.loadImages(con, doc, images);
        if (images.isEmpty())  throw new ExternalException("Icon for '"+name+"' is not found");
        return images;
    }

    private static Application.ServiceImages loadServiceImage(Connection con, String title, String href, String iconUrl)
    {
        Application.ServiceImages images = new Application.ServiceImages(title, href);
        Application.SiteImageItem image = new Application.SiteImageItem(Application.SiteImageItem.UNKNOWN, 1, iconUrl);
        Application.loadImage(con, images, image);
        return images;
    }

    public static Application.ServiceImages loadServiceImage(String title, String href, String iconUrl)
    {
        Application.ServiceImages images = new Application.ServiceImages(title, href);
        Application.SiteImageItem image = new Application.SiteImageItem(Application.SiteImageItem.UNKNOWN, 1, iconUrl);
        Application.loadImage(Application.getConnection(iconUrl), images, image, true);
        return images;
    }

    private static String getServiceName(URL url, String serviceBaseUrl, String serviceBaseName)
    {
        String host = url.getHost().toLowerCase();
        host = Util.cutIfStarts(host, "www.");
        String path = url.getPath().toLowerCase();
        path = Util.cutIfStarts(path, "/");
        return host.equals(serviceBaseUrl) ? Util.isEmpty(path) ? serviceBaseName : Util.sliceBefore(path, '/') :
               host.endsWith("."+serviceBaseUrl) ? Util.cutIfEnds(host, "."+serviceBaseUrl) :
               Util.sliceBeforeLast(host, '.');
    }

    private static void replaceSvg(Application.ServiceImages images, String svg) throws IOException, ExternalException, SVGException
    {
        for (int i=0; i<images.size(); i++)  {
            Application.SiteImageItem image = images.get(i);
            if (image.size==Application.SiteImageItem.ANY_SIZE)  {
                images.remove(i);
                Application.loadSvg(svg.getBytes("UTF8"), images, image);
                return;
            }
        }
    }
}
