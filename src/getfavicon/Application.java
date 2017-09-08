package getfavicon;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.cache.*;
import com.kitfox.svg.SVGDiagram;
import com.kitfox.svg.SVGException;
import com.kitfox.svg.SVGUniverse;
import net.sf.image4j.codec.ico.ICODecoder;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.ByteArray;
import utils.ExternalException;
import utils.StringList;
import utils.Util;

import javax.imageio.ImageIO;
import javax.net.ssl.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * User: And390
 * Date: 17.06.15
 * Time: 0:19
 */
public class Application
{
    private static Logger log = LoggerFactory.getLogger(Servlet.class);

    public enum Format { PNG, ICO, GIF, BMP, JPEG, SVG }

    public static final Map<String, Format> REQUEST_FORMAT_MAP = new HashMap<>();
    static  {
        for (Format format : Format.values())  REQUEST_FORMAT_MAP.put(format.name(), format);
        REQUEST_FORMAT_MAP.put("JPG", Format.JPEG);
        REQUEST_FORMAT_MAP.remove("SVG");
    }

    public static final Set<Format> SUPPORT_ALPHA = new HashSet<>();
    static  {
        SUPPORT_ALPHA.add(Format.PNG);
        SUPPORT_ALPHA.add(Format.GIF);
        SUPPORT_ALPHA.add(Format.ICO);
    }

    public static final Map<String, Format> CONTENT_TYPE_FORMATS = new HashMap<>();
    static  {
        CONTENT_TYPE_FORMATS.put("image/jpeg", Format.JPEG);
        CONTENT_TYPE_FORMATS.put("image/pjpeg", Format.JPEG);
        CONTENT_TYPE_FORMATS.put("image/png", Format.PNG);
        CONTENT_TYPE_FORMATS.put("image/gif", Format.GIF);
        CONTENT_TYPE_FORMATS.put("image/bmp", Format.BMP);
        CONTENT_TYPE_FORMATS.put("image/x-bmp", Format.BMP);
        CONTENT_TYPE_FORMATS.put("image/x-ms-bmp", Format.BMP);
        CONTENT_TYPE_FORMATS.put("image/x-icon", Format.ICO);
        CONTENT_TYPE_FORMATS.put("image/vnd.microsoft.icon", Format.ICO);
        CONTENT_TYPE_FORMATS.put("image/svg+xml", Format.SVG);
    }

    public static class Request {
        public String url;
        public int size = 16;
        public Format format = Format.PNG;
        public Request(String url_)  {  url=url_;  }
    }

    public static class SiteImages extends ArrayList<SiteImageItem> {

        // find insert position
        public int find(int itemSize)  {
            for (int i=0; ; i++)  if (i==size() || get(i).size>=itemSize)  return i;
        }

        // add into sorted array with respect to priorities
        public boolean add(SiteImageItem item)  {
            int i = find(item.size);
            if (i==size() || item.size != get(i).size)  add(i, item);
            else if (item.priority < get(i).priority)  set(i, item);
            return true;
        }

        //
        public void sort()  {
            Collections.sort(this, new Comparator<Application.SiteImageItem>() {
                @Override
                public int compare(Application.SiteImageItem o1, Application.SiteImageItem o2) {
                    return o1.size - o2.size;
                }
            });
        }

        public String toString()  {
            StringList buffer = new StringList ();
            for (SiteImageItem i : this)  buffer.append(i).nl();
            return buffer.toString();
        }

        public int getScalable() {
            return size() != 0 && get(0).size == SiteImageItem.ANY_SIZE ? 0 : -1;
        }
    }

    public static class SiteImageItem
    {
        public static final int UNKNOWN = -1;
        public static final int ANY_SIZE = -2;

        public final int size;
        public final int priority;
        public String url;
        @JsonIgnore public final BufferedImage image;
        @JsonIgnore public final SVGDiagram diagram;
        @JsonIgnore public final boolean buttonShape;

        public SiteImageItem(int size_, int priority_, String url_)  {  size = size_;  priority = priority_;  url = url_;  image = null;  diagram = null;  buttonShape = false;  }
        public SiteImageItem(int size_, int priority_, String url_, BufferedImage image_)  {
            size = size_;  priority = priority_;  url = url_;  image = image_;  diagram = null;
            buttonShape = checkButtonShape(image_, size);
        }
        public SiteImageItem(int size_, int priority_, String url_, SVGDiagram diagram_)  throws SVGException  {
            size = size_;  priority = priority_;  url = url_;  image = null;  diagram = diagram_;
            buttonShape = checkButtonShape(diagram_, Math.round(Math.max(diagram_.getWidth(), diagram_.getHeight())));
        }

        public boolean isLoaded()  {  return image != null || diagram != null;  }

        @Override
        public String toString()  {  String sz = size== UNKNOWN ? "?" : size==ANY_SIZE ? "_" : ""+size;  return sz+"x"+sz+" "+priority+" "+url;  }

        @Override
        public boolean equals(Object object)  {
            if (this == object)  return true;
            if (object == null || getClass() != object.getClass())  return false;
            SiteImageItem item = (SiteImageItem) object;
            return size==item.size && priority==item.priority && url.equals(item.url);
        }

        @Override
        public int hashCode()  {
            int result = size;
            result = 31 * result + priority;
            result = 31 * result + url.hashCode();
            return result;
        }
    }

    public static class ServiceImages extends SiteImages
    {
        public final String name;
        public final String url;

        public ServiceImages(String name, String url)  {  this.name=name;  this.url=url;  }
    }

    private static LoadingCache<String, SiteImages> cache = CacheBuilder.newBuilder()
        .maximumSize(10000)    // максимальное количество элементов в кэше
        .concurrencyLevel(10)  // следует установить равным количеству обрабатывающих потоков
        .expireAfterAccess(1, TimeUnit.DAYS)  // через день данные удаляются из кэша
        .removalListener(new RemovalListener<String, SiteImages>() {
            public void onRemoval(RemovalNotification<String, SiteImages> removalNotification) {

            }
        })
        .build(new CacheLoader<String, SiteImages>() {
            // загрузка отсутствующего элемента в кэше
            public SiteImages load(String url) throws Exception {
                return loadImages(url);
            }
        });


    private static HashMap<String, PresetImage> sewnImages = new HashMap<>();
    private static class PresetImage {
        byte[] data;
        Format format;
    }
    public static void init() throws IOException, GeneralSecurityException
    {
        File[] files = new File("site_img").listFiles();
        if (files == null)  {  log.warn("'site_img' directory is not found");  return;  }
        for (File file : files)  {
            String name = file.getName();
            String ext = Util.fileExt(name, true);
            if (ext == null)  continue;
            Format format = REQUEST_FORMAT_MAP.get(ext.toUpperCase());
            if (format == null)  {  log.warn("Unexpected file in 'site_img' directory: " + name);  continue;  }
            String domain = name.substring(0, name.length() - ext.length() - 1);
            byte[] imgData = ByteArray.read(file);
            PresetImage img = new PresetImage();
            img.data = imgData;
            img.format = format;
            sewnImages.put(domain, img);
        }

        disableSSLCertCheck();
    }

    public static BufferedImage process(Request request, String requestSize, String requestFormat, boolean button)
            throws ExternalException, IOException, SVGException
    {
        //    parse parameters
        try  {
            if (request.url.isEmpty())  throw new ExternalException ("empty URL");
            if (Util.isNotEmpty(requestSize))  request.size = Util.getInt(requestSize, "size", 1, 1024);
            if (Util.isNotEmpty(requestFormat))  request.format = Util.get(requestFormat.toUpperCase(), "format", REQUEST_FORMAT_MAP);
        }
        catch (ExternalException e)  {  throw new BadRequestException (e.getMessage());  }

        SiteImages siteImages;
        //    services
        String cuttedRequestUrl = Util.cutIfEnds(request.url, "/");
        int s = cuttedRequestUrl.indexOf('/');
        Map<String, ServiceImages> serviceImages = s==-1 ? null : ServiceParser.services.get(cuttedRequestUrl.substring(0, s));
        if (serviceImages != null)
        {
            //    get service name
            request.url = cuttedRequestUrl;
            String service = cuttedRequestUrl.substring(s+1);
            if (service.isEmpty())  throw new BadRequestException("Empty service name for " + request.url);

            //    get loaded images
            siteImages = serviceImages.get(service);
            if (siteImages == null)  throw new BadRequestException("Unknow service: " + request.url);
        }
        else if (ServiceParser.otherServices.containsKey(cuttedRequestUrl))
        {
            request.url = cuttedRequestUrl;
            siteImages = ServiceParser.otherServices.get(cuttedRequestUrl);
        }
        //    sites
        else
        {
            //    add default protocol
            int i = request.url.indexOf("://");
            if (i==-1 || request.url.lastIndexOf(":", i-1)!=-1 || request.url.lastIndexOf("/", i-1)!=-1) {
                request.url = "http://" + request.url;
            }
            //    check URL
            URL url;
            try  {  url = new URL (request.url);  }
            catch (MalformedURLException e)  {  throw new BadRequestException (e.getMessage());  }
            //    check domain is a known service
            String domain = Util.cutIfStarts(url.getHost(), "www.");
            siteImages = ServiceParser.servicesByDomain.get(domain);
            if (siteImages == null) {
                //    remove ending slash
                request.url = Util.cutIfEnds(request.url, "/");
                //    remove www prefix
                request.url = request.url.startsWith("http://www.") ? "http://" + request.url.substring("http://www.".length()) :
                              request.url.startsWith("https://www.") ? "https://" + request.url.substring("https://www.".length()) : request.url;

                //    get icon images from cache or load
                try  {  siteImages = cache.get(request.url);  }
                catch (ExecutionException e)  {
                    if (e.getCause() instanceof IOException)  throw new ExternalException (e.getCause());
                    else  throw new RuntimeException (e.getCause());
                }
            }
        }

        //    find image
        int requiredSize = button ? Math.round(request.size * 0.875f) : request.size;
        SiteImageItem imageItem = getAppropriateImage(siteImages, requiredSize, request.size);
        if (imageItem.buttonShape)  requiredSize = request.size;

        //    draw svg if needed
        BufferedImage original = imageItem.image;
        BufferedImage image = original != null ? original : drawSvg(imageItem.diagram, requiredSize);

        //    make button or resize image if necessary
        if (button)  {
            if (image == original || image.getType() != BufferedImage.TYPE_INT_ARGB)  {
                BufferedImage old = image;
                image = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
                image.getGraphics().drawImage(old, 0, 0, null);
            }
            replaceWhiteBackgroundWithAlpha(image);
            image = getScaledImage(image, requiredSize, requiredSize, request.size, request.size);
            drawButton(image, request.size);
        }
        else if (image.getWidth()!=request.size)  image = getScaledImage(image, request.size, request.size);

        //    remove transparecy if need
        if (image.getColorModel().hasAlpha() && !SUPPORT_ALPHA.contains(request.format)) {
            BufferedImage old = image;
            image = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = image.createGraphics();
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, image.getWidth(), image.getHeight());
            g2d.drawImage(old, 0, 0, null);
            g2d.dispose();
        }

        return image;
    }

    public static BufferedImage drawError(int size, String err)  {
        return drawText(size, "ERR", err, Color.RED);
    }
    public static BufferedImage drawText(int size, String line1, String line2, Color color)  {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics g = image.createGraphics();
        g.setColor(color);
        g.setFont(new Font("Lucida Sans TypeWriter", Font.BOLD, size / 2));
        FontMetrics fontMetrics = g.getFontMetrics();
        g.drawString(line1, size/2 - fontMetrics.stringWidth(line1)/2, size/2 - 2);
        g.drawString(line2, size/2 - fontMetrics.stringWidth(line2)/2, size - 2);
        return image;
    }

    // ищется ссылка с размером, равным запрашиваемому, если нет, то ближайшая с большим размером и кратным запрашиваемому,
    // если нет, то просто ближайшая с большим, если нет, то ближайшая с меньшим
    // с равным размером выбирается ссылка с наибольшим приоритетом
    public static boolean isMoreAppropriate(int requestSize, int foundSize, int foundPriority, int size, int priority)  {
        return
            size==foundSize ? priority<foundPriority : foundSize==requestSize ? false :
            foundSize>requestSize ? size>=requestSize && (size%requestSize==0 && foundSize%requestSize!=0
                || size<foundSize && (size%requestSize==0 || foundSize%requestSize!=0)) :
            size>foundSize;
    }

    public static SiteImageItem getAppropriateImage(SiteImages images, int requestSize, int buttonShapeSize) throws NotFoundException
    {
        synchronized (images)  {  //todo плохо, что при загрузке доп. изображений блокируется доступ на чтение к уже загруженным
            while (true)  {
                if (images.isEmpty())  throw new NotFoundException();

                //    try to get equal or greater size
                int i=0;
                for (; i!=images.size() && images.get(i).size < (images.get(i).buttonShape ? buttonShapeSize : requestSize); )  i++;
                //  if size is not exactly the same and there is a scalable image - use scalable
                if ((i==images.size() || images.get(i).size != (images.get(i).buttonShape ? buttonShapeSize : requestSize)) && images.getScalable()!=-1)  i = images.getScalable();
                //  if no, get last
                else if (i==images.size())  i--;
                //  if size is not multiple of requested size then try to find it bigger
                else  {
                    for (int j=i; j!=images.size(); j++)
                        if (images.get(j).size % (images.get(j).buttonShape ? buttonShapeSize : requestSize) == 0)  {  i = j;  break;  }
                }

                //    return if loaded or else load and refind
                SiteImageItem image = images.get(i);
                if (image.isLoaded())  return image;
                else  {
                    SiteImageItem item = images.remove(i);
                    loadImage(getConnection(item.url), images, item);
                }
            }
        }
    }


    //                --------    image loading    --------

    public static SiteImages loadImages(String url) throws IOException  {  return loadImages(url, new SiteImages());  }

    public static SiteImages loadImages(String url, SiteImages siteImages) throws IOException
    {
        //    execute
        Connection con = getConnection(url);
        Connection.Response response = null;
        try  {  response = con.execute();  }  catch (IOException e)  {
            if (loadSewnImage(url, siteImages).isEmpty())  throw new IOException("Can't get http: "+url, e);
            else  {  log.error("Can't get http: "+url, e);  return siteImages;  }
        }
        String contentType = getPureContentType(response);
        Format imgFormat = CONTENT_TYPE_FORMATS.get(contentType);
        if (imgFormat != null || contentType != null && contentType.startsWith("image/"))  {
            loadImage(response, siteImages, new SiteImageItem(SiteImageItem.UNKNOWN, 1, url), imgFormat);
        }
        else  {
            Document document = response.parse();
            loadImages(con, document, siteImages);
        }

        //    append preset images
        return loadSewnImage(url, siteImages);
    }

    private static SiteImages loadSewnImage(String url, SiteImages siteImages) throws MalformedURLException
    {
        String domain = new URL(url).getHost();
        PresetImage sewn = sewnImages.get(domain);
        if (sewn != null) {
            SiteImageItem item = new Application.SiteImageItem(Application.SiteImageItem.UNKNOWN, 1, url);
            loadImage(sewn.data, siteImages, item, sewn.format, false);
        }
        return siteImages;
    }

    public static SiteImages loadImages(Connection con, Document document, SiteImages siteImages) throws IOException
    {
        //    parse HTML
        LinkedHashMap<String, SiteImageItem> items = new LinkedHashMap<> ();
        Consumer<SiteImageItem> addItem = (item) -> {
            items.compute(item.url, (k,v) -> v == null || item.priority < v.priority || item.priority == v.priority && v.size == SiteImageItem.UNKNOWN
                    ? item : v);
        };
        URL base = new URL(document.baseUri());
        boolean hasP1 = false;
        String lastOgImageUrl = null;
        int lastOgImageWidth = 0;
        int lastOgImageHeight = 0;
        for (Element elem : document.head().children())  {
            if (elem.tagName().equals("link"))  {
                int priority = 0;
                String iconUrl = null;
                int size = SiteImageItem.UNKNOWN;
                switch (elem.attr("rel").toLowerCase())  {
                    case "icon":
                    case "shortcut icon":
                        priority = 1;  iconUrl = elem.attr("href");  hasP1 = true;  break;
                    case "apple-touch-icon":
                    case "apple-touch-icon-precomposed":
                        priority = 2;  iconUrl = elem.attr("href");
                        size = parseSize(elem.attr("sizes").toLowerCase());
                        if (size == 0)  continue;
                        break;
                    case "fluid-icon":
                        priority = 3;  iconUrl = elem.attr("href");
                        size = parseSize(elem.attr("sizes").toLowerCase());
                        if (size == 0)  continue;
                        break;
                }
                if (priority!=0 && Util.isNotEmpty(iconUrl))  {
                    addItem.accept(new SiteImageItem(size, priority, iconUrl));
                }
            }
            else if (elem.tagName().equals("meta"))  {
                String property = elem.attr("property");
                String content = elem.attr("content");
                if (!property.isEmpty() && !content.isEmpty())  {
                    if (property.equals("og:image"))
                    {
                        lastOgImageUrl = elem.attr("content");
                        lastOgImageWidth = 0;
                        lastOgImageHeight = 0;
                        addItem.accept(new SiteImageItem(-1, 4, lastOgImageUrl));
                    }
                    else if (property.equals("og:image:width") && lastOgImageWidth == 0)
                    {
                        if (lastOgImageUrl != null)
                            try  {
                                lastOgImageWidth = Integer.parseInt(content);
                                if (lastOgImageWidth <= 0)  throw new NumberFormatException();
                                if (lastOgImageHeight != 0)  {
                                    SiteImageItem lastOgImage = items.remove(lastOgImageUrl);
                                    if (lastOgImageHeight == lastOgImageWidth)  addItem.accept(new SiteImageItem(lastOgImageWidth, lastOgImage.priority, lastOgImageUrl));
                                    else  log.info("Different og:image sizes " + lastOgImageWidth + "x" + lastOgImageHeight + " for " + lastOgImageUrl);
                                }
                            }
                            catch (NumberFormatException e)  {  log.info("Wrong og:image:width = " + content + " for " + lastOgImageUrl);  }
                        else
                            log.info("Wrong og:image:width before og:image");
                    }
                    else if (property.equals("og:image:height") && lastOgImageHeight == 0)
                    {
                        if (lastOgImageUrl != null)
                            try  {
                                lastOgImageHeight = Integer.parseInt(content);
                                if (lastOgImageHeight <= 0)  throw new NumberFormatException();
                                if (lastOgImageWidth != 0)  {
                                    SiteImageItem lastOgImage = items.remove(lastOgImageUrl);
                                    if (lastOgImageHeight == lastOgImageWidth)  addItem.accept(new SiteImageItem(lastOgImageHeight, lastOgImage.priority, lastOgImageUrl));
                                    else  log.info("Different og:image sizes: " + lastOgImageWidth + "x" + lastOgImageHeight + " for " + lastOgImageUrl);
                                }
                            }
                            catch (NumberFormatException e)  {  log.info("Wrong og:image:height = " + content + " for " + lastOgImageUrl);  }
                        else
                            log.info("Wrong og:image:height before og:image");
                    }
                }
            }
            else if (elem.tagName().equals("base"))  {
                if (!elem.attr("href").isEmpty())
                    try  {  base = new URL(base, elem.attr("href"));  }
                    catch (MalformedURLException e)  {}  //ignore malformed base URLs
            }
        }

        //    append favicon.ico if no
        if (!hasP1)
            addItem.accept(new SiteImageItem(SiteImageItem.UNKNOWN, 1, "/favicon.ico"));

        //    append url base
        for (String key : new ArrayList<>(items.keySet()))  {
            try  {
                SiteImageItem item = items.remove(key);
                item.url = new URL(base, item.url).toString();
                addItem.accept(item);
            }
            catch (MalformedURLException e)  {  log.error(e.toString());  }  //ignore malformed URLs
        }

        //    order images by size (see SiteImages.add), load images without sizes
        for (SiteImageItem item : items.values())
            if (item.size!=SiteImageItem.UNKNOWN)  siteImages.add(item);
            else  loadImage(con, siteImages, item);

        return siteImages;
    }

    // One SiteImageItem can produce multiple SiteImageItem-s after loading (ICO case)
    public static void loadImage(Connection con, SiteImages items, SiteImageItem item)  {  loadImage(con, items, item, false);  }
    public static void loadImage(Connection con, SiteImages items, SiteImageItem item, boolean allowNonQuadSvg)
    {
        try
        {
            Connection.Response response = con.url(item.url).execute();
            String contentType = getPureContentType(response);
            Format imgFormat = CONTENT_TYPE_FORMATS.get(contentType);
            loadImage(response, items, item, imgFormat, allowNonQuadSvg);
        }
        catch (Exception e)  {
            log.error("Can't load "+item.url+": "+e.toString());
        }
    }

    private static void loadImage(Connection.Response response, SiteImages items, SiteImageItem item, Format format)  {  loadImage(response, items, item, format, false);  }
    private static void loadImage(Connection.Response response, SiteImages items, SiteImageItem item, Format format, boolean allowNonQuadSvg)  {  loadImage(response.bodyAsBytes(), items, item, format, allowNonQuadSvg);  }
    private static void loadImage(byte[] bytes, SiteImages items, SiteImageItem item, Format format, boolean allowNonQuadSvg)
    {
        try
        {
            //    read content
            byte[] content = bytes;

            //    try parse image with jdk first
            if (format != Format.SVG)  {   //  && format != Format.ICO  //there is a PNG disguised as ICO on some sites
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));
                if (image!=null)  {
                    checkAndAddImage(items, image, item);
                    return;
                }
            }

            //    try to parse SVG
            if (format == Format.SVG)  {
                loadSvg(content, items, item, allowNonQuadSvg);
                return;
            }

            //    parse with image4j if no support with jdk (ICO)
            if (format == Format.ICO || format == null)  {
                java.util.List<BufferedImage> images = ICODecoder.read(new ByteArrayInputStream(content));
                if (images.size()!=0)  {
                    for (BufferedImage iconImage : images)  checkAndAddImage(items, iconImage, item);
                    return;
                }
            }

            //    unsupported image
            throw new ExternalException("Unsupported image: "+item.url);
        }
        catch (Exception e)  {
            log.error("Can't load "+item.url+": "+e.toString());
        }
    }

    public static void loadSvg(byte[] content, SiteImages items, SiteImageItem item) throws ExternalException, IOException, SVGException  {
        loadSvg(content, items, item, false);
    }
    public static void loadSvg(byte[] content, SiteImages items, SiteImageItem item, boolean allowNonQuad) throws ExternalException, IOException, SVGException
    {
        SVGUniverse universe = new SVGUniverse();
        SVGDiagram diagram = universe.getDiagram(universe.loadSVG(new ByteArrayInputStream(content), item.url));
        if (!allowNonQuad && diagram.getWidth()!=diagram.getHeight())  throw new ExternalException(
            "Image has different sizes: "+diagram.getWidth()+"x"+diagram.getHeight());
        items.add(new SiteImageItem(SiteImageItem.ANY_SIZE, item.priority, item.url, diagram));
    }

    public static Connection getConnection(String url)
    {
        return Jsoup.connect(url)
            .ignoreHttpErrors(true)
            .ignoreContentType(true)
            .userAgent("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.36")
            .referrer("http://www.google.com")
            .timeout(12000);
            //.followRedirects(true)
    }

    // parse strings like '32x32'
    public static int parseSize(String value)  {
        int x=0;
        while (x!=value.length() && value.charAt(x)>='0' && value.charAt(x)<='9')  x++;
        if (x==0 || x+1>=value.length() || value.charAt(x)!='x')  return SiteImageItem.UNKNOWN;
        String v1 = value.substring(0, x);
        String v2 = value.substring(x+1);
        if (!v1.equals(v2))  return 0;
        int result = Integer.parseInt(v1);
        return result<=0 || result>=32*1024 ? 0 : result;
    }

    public static void checkAndAddImage(SiteImages items, BufferedImage image, SiteImageItem item) throws ExternalException  {
        //TODO special treatment for Yahoo
        if (image.getWidth()==250 && image.getHeight()==252)  {
            image = getSizedImage(image, 252, 252);
        }
        if (image.getWidth()!=image.getHeight())  {  //ignore
            log.info("Image has different sizes ("+image.getWidth()+"x"+image.getHeight()+"): "+item.url);
            return;
        }
        int size = image.getWidth();
        if (item.size != SiteImageItem.UNKNOWN && item.size != SiteImageItem.ANY_SIZE && size != item.size)  log.warn("Loaded image size differs from declared (" + size + " <> " + item.size + ") for " + item.url);
        items.add(new SiteImageItem(size, item.priority, item.url, image));
    }

    private static String getPureContentType(Connection.Response response)  {
        String contentType = response.contentType();
        if (contentType == null)  return null;
        int i = contentType.indexOf(';');
        return i != -1 ? contentType.substring(0, i).trim() : contentType;
    }


    //                --------    image processing    --------

    public static BufferedImage drawSvg(SVGDiagram diagram, int size) throws SVGException
    {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        AffineTransform at = new AffineTransform();
        float maxSize = Math.max(diagram.getWidth(), diagram.getHeight());
        at.setToScale(size/maxSize, size/maxSize);
        if (diagram.getWidth() != diagram.getHeight())
            at.translate((maxSize - diagram.getWidth())/2.0, (maxSize - diagram.getHeight())/2.0);
        g2.transform(at);
        diagram.render(g2);
        return image;
    }

    public static BufferedImage getScaledImage(BufferedImage image, int drawWidth, int drawHeight) {
        return getScaledImage(image, drawWidth, drawHeight, drawWidth, drawHeight);
    }

    public static BufferedImage getScaledImage(BufferedImage image, int drawWidth, int drawHeight, int destWidth, int destHeight)
    {
        int imageWidth  = image.getWidth();
        int imageHeight = image.getHeight();

        if (imageWidth == drawWidth && imageHeight == drawHeight && imageWidth == destWidth && imageHeight == destHeight)
            return image;

        double scaleX = (double)drawWidth/imageWidth;
        double scaleY = (double)drawHeight/imageHeight;
        AffineTransform scaleTransform = new AffineTransform();
        if (destWidth != drawWidth || destHeight != drawHeight)  {
            scaleTransform.translate((destWidth - drawWidth)/2.0, (destHeight - drawHeight)/2.0);
        }
        scaleTransform.scale(scaleX, scaleY);
        AffineTransformOp bilinearScaleOp = new AffineTransformOp(scaleTransform, AffineTransformOp.TYPE_BILINEAR);

        return bilinearScaleOp.filter(
                image,
                new BufferedImage(destWidth, destHeight, image.getType()));
    }

    public static BufferedImage getSizedImage(BufferedImage image, int destWidth, int destHeight)
    {
        int imageWidth  = image.getWidth();
        int imageHeight = image.getHeight();

        if (imageWidth == destWidth && imageHeight == destHeight)  return image;

        AffineTransform transform = new AffineTransform();
        transform.translate((destWidth - imageWidth)/2.0, (destHeight - imageHeight)/2.0);
        AffineTransformOp bilinearScaleOp = new AffineTransformOp(transform, AffineTransformOp.TYPE_BILINEAR);

        return bilinearScaleOp.filter(
                image,
                new BufferedImage(destWidth, destHeight, image.getType()));
    }

    // image must be TYPE_INT_ARGB
    private static void drawButton(BufferedImage image, int size)
    {
        int[] data = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        for (int y=0, i=0; y<size; y++)  {
            for (int x=0; x<size; x++, i++)  {
                //  calc distance to border
                float border;
                float borderWidth = 1;
                float borderRad = size * 0.175f;
                float borderAlpha = 0.25f;
                float x2 = size-1 - x;
                float y2 = size-1 - y;
                if (x < borderRad && y < borderRad)  border = borderRad - (float)Math.sqrt((x-borderRad)*(x-borderRad) + (y-borderRad)*(y-borderRad));
                else if (x2 < borderRad && y < borderRad)  border = borderRad - (float)Math.sqrt((x2-borderRad)*(x2-borderRad) + (y-borderRad)*(y-borderRad));
                else if (x < borderRad && y2 < borderRad)  border = borderRad - (float)Math.sqrt((x-borderRad)*(x-borderRad) + (y2-borderRad)*(y2-borderRad));
                else if (x2 < borderRad && y2 < borderRad)  border = borderRad - (float)Math.sqrt((x2-borderRad)*(x2-borderRad) + (y2-borderRad)*(y2-borderRad));
                else if (x < borderWidth)  border = x;
                else if (y < borderWidth)  border = y;
                else if (x2 < borderWidth)  border = x2;
                else if (y2 < borderWidth)  border = y2;
                else  border = borderWidth;
                border /= borderWidth;
                float r = 0;
                float g = 0;
                float b = 0;
                float a = borderAlpha;
                if (border > 0.0)  {
                    //  get source color
                    int p = data[i];
                    float sa = ((p >> 24) & 0xFF) / 255f;
                    float sr = ((p >> 16) & 0xFF) / 255f;
                    float sg = ((p >> 8) & 0xFF) / 255f;
                    float sb = (p & 0xFF) / 255f;
                    //  calc background color
                    float h = y / (size-1f);
                    h *= 2;
                    float grey = h < 1 ? (1 - h*h*h*h * 0.1f) : (0.9f - (h-1)*(h-1)*(h-1)*(h-1) * 0.1f);
                    float br = grey;
                    float bg = grey;
                    float bb = grey;
                    float ba = 1;
                    //  append
                    float a2 = (1 - sa) * ba;
                    a = sa + a2;
                    r = (sr * sa + br * a2) / a;
                    g = (sg * sa + bg * a2) / a;
                    b = (sb * sa + bb * a2) / a;
                    //  append border
                    if (border < 1)  {
                        float k = border;
                        r = r * k;
                        g = g * k;
                        b = b * k;
                        a = a * k + borderAlpha * (1 - k);
                    }
                }
                else if (border < -0.5)  {
                    a = 0;
                }
                //  write
                data[i] = Math.round(b*255) + (Math.round(g*255) << 8) + (Math.round(r*255) << 16) + (Math.round(a*255) << 24);
            }
        }
    }

    // image must be TYPE_INT_ARGB
    public static void replaceWhiteBackgroundWithAlpha(BufferedImage image)
    {
        new WhiteBackgroundReplacer(image).replace();
    }

    private static class WhiteBackgroundReplacer
    {
        private static final int DETECT_COLOR_THRESHOLD = Math.round(255 * 3 * 0.90f);
        private static final int REPLACE_COLOR_THRESHOLD = Math.round(255 * 3 * 0.70f);
        private static final float DETECT_BORDER_THRESHOLD = 0.1f;
        private static final float DETECT_PIXELS_THRESHOLD = 0.85f;

        int[] data;
        boolean[] passed;
        boolean[] marked;
        int count;

        int width;
        int height;
        int widthThreshold;
        int heightThreshold;

        WhiteBackgroundReplacer(BufferedImage image)
        {
            data = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();

            width = image.getWidth();
            height = image.getHeight();
            widthThreshold = (int) Math.ceil(width * DETECT_BORDER_THRESHOLD);
            heightThreshold = (int) Math.ceil(height * DETECT_BORDER_THRESHOLD);
        }

        void replace()
        {
            if (check())  doReplace();
        }

        void doReplace()
        {
            //    Background detected, add alpha
            for (int i=0; i<data.length; i++)  {
                int p = data[i];
                int r = (p >> 16) & 0xFF;
                int g = (p >> 8) & 0xFF;
                int b = p & 0xFF;
                if (r + g + b >= REPLACE_COLOR_THRESHOLD)  {
                    float a = (255*3 - (r + g + b)) / (float)(255*3 - REPLACE_COLOR_THRESHOLD);
                    r = Math.round((1 - (1 - r/255f) / a) * 255);
                    g = Math.round((1 - (1 - g/255f) / a) * 255);
                    b = Math.round((1 - (1 - b/255f) / a) * 255);
                    data[i] = b + (g << 8) + (r << 16) + (Math.round(a*255) << 24);
                }
            }
        }

        boolean check()
        {
            if (checkHasAlpha(data))  return false;

            passed = new boolean [data.length];
            marked = new boolean [data.length];
            count = 0;

            for (int x=0; x<width; x++)  check(x, 0);
            for (int x=0; x<width; x++)  check(x, height-1);
            for (int y=0; y<height; y++)  check(0, y);
            for (int y=0; y<height; y++)  check(width-1, y);

            int total = 2 * widthThreshold * height + 2 * heightThreshold * width - 4 * widthThreshold * heightThreshold;
            return count >= total * DETECT_PIXELS_THRESHOLD;
        }

        private void check(int x, int y)
        {
            if (x < 0 || x >= width || y < 0 || y >= height)  return;
            if (x >= widthThreshold && y >= heightThreshold && x < width - widthThreshold && y < height - heightThreshold)  return;
            int i = y * width + x;
            if (passed[i])  return;
            int p = data[i];
            int r = (p >> 16) & 0xFF;
            int g = (p >> 8) & 0xFF;
            int b = p & 0xFF;
            passed[i] = true;
            if (r + g + b >= DETECT_COLOR_THRESHOLD)  {
                marked[i] = true;
                count++;
                check(x, y-1);
                check(x-1, y);
                check(x, y+1);
                check(x+1, y);
            }
        }

        private static boolean checkHasAlpha(int[] data)
        {
            for (int i=0; i<data.length; i++)  {
                int a = (data[i] >> 24) & 0xFF;
                if (a != 255)  return true;
            }
            return false;
        }
    }

    public static boolean checkButtonShape(SVGDiagram diagram, int size) throws SVGException
    {
        BufferedImage image = drawSvg(diagram, size);
        return checkButtonShape(image, size);
    }
    public static boolean checkButtonShape(BufferedImage image, int size)
    {
        // image has another type - copy image with necessary type, then check and replace white background
        // image has necessary type - check white background, copy image if need replace background, then replace
        WhiteBackgroundReplacer whiteBackgroundReplacer = null;
        boolean needChangeImageType = image.getType() != BufferedImage.TYPE_INT_ARGB;
        boolean needReplaceWhite = false;
        if (!needChangeImageType)  {
            whiteBackgroundReplacer = new WhiteBackgroundReplacer(image);
            needReplaceWhite = whiteBackgroundReplacer.check();
        }
        if (needChangeImageType || needReplaceWhite)  {
            BufferedImage old = image;
            image = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
            image.getGraphics().drawImage(old, 0, 0, null);
        }
        if (whiteBackgroundReplacer==null)  {
            whiteBackgroundReplacer = new WhiteBackgroundReplacer(image);
            needReplaceWhite = whiteBackgroundReplacer.check();
        }
        if (needReplaceWhite)  whiteBackgroundReplacer.doReplace();

        final float THRESHOLD = 0.66f;
        int[] data = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        check:  for (int y : new int[] { 0, size-1})  {
            find:  for (int d=0; d<=1 && d*2 < size; d++)  {
                int line = (y + (y==0 ? d : -d)) * size;
                int x1 = d;
                while (x1 < size && getAlpha(data[line+x1]) == 0)  x1++;
                int x2 = size - 1 - d;
                while (x2 > x1 && getAlpha(data[line+x2]) == 0)  x2--;
                for (int x=x1+1; x<x2; x++)  if (getAlpha(data[line+x])==0)  continue find;
                if ((x2 - x1) / (float)(size - d*2) < THRESHOLD)  continue;
                continue check;  //found line - check next line
            }
            return false;
        }
        check:  for (int x : new int[] { 0, size-1})  {
            find:  for (int d=0; d<=1 && d*2 < size; d++)  {
                int shift = x + (x==0 ? d : -d);
                int y1 = d;
                while (y1 < size && getAlpha(data[y1*size+shift]) == 0)  y1++;
                int y2 = size - 1 - d;
                while (y2 > y1 && getAlpha(data[y2*size+shift]) == 0)  y2--;
                for (int y=y1+1; y<y2; y++)  if (getAlpha(data[y*size+shift])==0)  continue find;
                if ((y2 - y1) / (float)size < THRESHOLD)    continue;
                continue check;  //found line - check next line
            }
            return false;
        }
        return true;
    }

    private static int getAlpha(int rgba)  {  return (rgba >> 24) & 0xFF;  }


    //                --------    util    --------

    private static void disableSSLCertCheck() throws NoSuchAlgorithmException, KeyManagementException {
        // Create a trust manager that does not validate certificate chains
        TrustManager[] trustAllCerts = new TrustManager[] {new X509TrustManager() {
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return null;
                }
                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                }
                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                }
            }
        };

        // Install the all-trusting trust manager
        SSLContext sc = SSLContext.getInstance("SSL");
        sc.init(null, trustAllCerts, new java.security.SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

        // Create all-trusting host name verifier
        HostnameVerifier allHostsValid = new HostnameVerifier() {
            public boolean verify(String hostname, SSLSession session) {
                return true;
            }
        };

        // Install the all-trusting host verifier
        HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
    }
}
