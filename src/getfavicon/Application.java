package getfavicon;

import com.google.common.cache.*;
import net.sf.image4j.codec.ico.ICODecoder;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import utils.ByteArray;
import utils.ExternalException;
import utils.StringList;
import utils.Util;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * User: And390
 * Date: 17.06.15
 * Time: 0:19
 */
public class Application
{

    public static enum Format { PNG, ICO, GIF, BMP, JPEG }

    public static final Map<String, Format> FORMAT_MAP = new HashMap<>();
    static  {
        for (Format format : Format.values())  FORMAT_MAP.put(format.name(), format);
        FORMAT_MAP.put("JPG", Format.JPEG);
    }

    public static final Set<Format> SUPPORT_ALPHA = new HashSet<>();
    static  {
        SUPPORT_ALPHA.add(Format.PNG);
        SUPPORT_ALPHA.add(Format.GIF);
        SUPPORT_ALPHA.add(Format.ICO);
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
            for (int i=0; ; i++)  if (i==size() || get(i).size>itemSize)  return i;
        }

        // add with respect to priorities
        public boolean add(SiteImageItem item)  {
            int i = find(item.size);
            if (i==size() || item.size != get(i).size || item.priority < get(i).priority)  add(i, item);
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
    }

    public static class SiteImageItem {
        public final int size;
        public final int priority;
        public String url;
        public BufferedImage image;  //null if not loaded
        public SiteImageItem(int size_, int priority_, String url_)  {  size = size_;  priority = priority_;  url = url_;  }

        @Override
        public String toString()  {  return (size==-1?"?":""+size)+"x"+(size==-1?"?":""+size)+" "+priority+" "+url;  }

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

    private static LoadingCache<String, SiteImages> cache = CacheBuilder.newBuilder()
        .maximumSize(10000)    // максимальное количество элементов в кэше
        .concurrencyLevel(1)  // следует установить равным количеству обрабатывающих потоков, что зависит от настройки сервера
        .expireAfterAccess(1, TimeUnit.DAYS)  // через день данные удал€ютс€ из кэша и сохран€ютс€ в базу
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


    public static BufferedImage process(Request request, String requestSize, String requestFormat)
            throws ExternalException, IOException
    {
        //    add default protocol
        int i = request.url.indexOf("://");
        if (i==-1 || request.url.lastIndexOf(":", i-1)!=-1 || request.url.lastIndexOf("/", i-1)!=-1) {
            request.url = "http://" + request.url;
        }

        //    parse parameters
        try  {
            if (request.url.isEmpty())  throw new ExternalException ("empty URL");
            new URL (request.url);
            if (Util.isNotEmpty(requestSize))  request.size = Util.getInt(requestSize, "size", 1, 256);
            if (Util.isNotEmpty(requestFormat))  request.format = Util.get(requestFormat.toUpperCase(), "format", FORMAT_MAP);
        }
        catch (ExternalException|MalformedURLException e)  {  throw new BadRequestException (e.getMessage());  }

        //    get icon images from cache or load
        SiteImages siteImages;
        try  {  siteImages = cache.get(request.url);  }
        catch (ExecutionException e)  {
            if (e.getCause() instanceof IOException)  throw new ExternalException (e.getCause());
            else  throw new RuntimeException (e.getCause());
        }

        //    find image
        BufferedImage image = getAppropriateImage(siteImages, request.size);

        //    resize image
        if (image.getWidth()!=request.size)  image = getScaledImage(image, request.size, request.size);

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

    public static BufferedImage processError(Request request, String errorCode)  {
        BufferedImage image = new BufferedImage(request.size, request.size, BufferedImage.TYPE_INT_RGB);
        Graphics g = image.createGraphics();
        g.setColor(Color.RED);
        g.setFont(new Font("Lucida Sans TypeWriter", Font.PLAIN, request.size / 2));
        FontMetrics fontMetrics = g.getFontMetrics();
        g.drawString("ERR", request.size/2 - fontMetrics.stringWidth("ERR")/2, request.size/2);
        g.drawString(errorCode, request.size/2 - fontMetrics.stringWidth(errorCode)/2, request.size);
        return image;
    }

    // ищетс€ ссылка с размером, равным запрашиваемому, если нет, то ближайша€ с большим размером и кратным запрашиваемому,
    // если нет, то просто ближайша€ с большим, если нет, то ближайша€ с меньшим
    // с равным размером выбираетс€ ссылка с наибольшим приоритетом
    public static boolean isMoreAppropriate(int requestSize, int foundSize, int foundPriority, int size, int priority)  {
        return
            size==foundSize ? priority<foundPriority : foundSize==requestSize ? false :
            foundSize>requestSize ? size>=requestSize && (size%requestSize==0 && foundSize%requestSize!=0
                || size<foundSize && (size%requestSize==0 || foundSize%requestSize!=0)) :
            size>foundSize;
    }

    public static BufferedImage getAppropriateImage(SiteImages images, int requestSize) throws NotFoundException
    {
        synchronized (images)  {  //todo плохо, что при загрузке доп. изображений блокируетс€ доступ на чтение к уже загруженным
            while (true)  {
                if (images.isEmpty())  throw new NotFoundException();

                //    try get bigger size
                int i=0;
                for (; i!=images.size() && images.get(i).size < requestSize; )  i++;
                if (i!=images.size())  {
                    //  if size is not multiple of requested size then try to find it bigger
                    for (int j=i; j!=images.size(); j++)
                        if (images.get(j).size % requestSize == 0)  {  i = j;  break;  }
                }
                //    if no, get last lower
                else  {
                    i--;
                }

                //    return if loaded or else load and refind
                BufferedImage image = images.get(i).image;
                if (image!=null)
                    return image;
                else  {
                    SiteImageItem item = images.remove(i);
                    loadImage(images, item);
                }
            }
        }
    }

    public static BufferedImage getScaledImage(BufferedImage image, int width, int height) {
        int imageWidth  = image.getWidth();
        int imageHeight = image.getHeight();

        double scaleX = (double)width/imageWidth;
        double scaleY = (double)height/imageHeight;
        AffineTransform scaleTransform = AffineTransform.getScaleInstance(scaleX, scaleY);
        AffineTransformOp bilinearScaleOp = new AffineTransformOp(scaleTransform, AffineTransformOp.TYPE_BILINEAR);

        return bilinearScaleOp.filter(
                image,
                new BufferedImage(width, height, image.getType()));
    }


    //                --------    image loading    --------

    public static SiteImages loadImages(String url) throws IOException {
        //    execute
        Document document = Jsoup.connect(url)
            .ignoreHttpErrors(true)
            .ignoreContentType(true)
            .userAgent("Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:38.0) Gecko/20100101 Firefox/38.0")
            .referrer("http://www.google.com")
            .timeout(12000)
            //.followRedirects(true)
            .get();

        //    parse HTML
        List<SiteImageItem> items = new ArrayList<> ();
        String base = url;
        for (Element elem : document.head().children())  {
            if (elem.tagName().equals("link"))  {
                int priority = 0;
                String iconUrl = null;
                int size = -1;
                switch (elem.attr("rel").toLowerCase())  {
                    case "icon":
                    case "shortcut icon":
                        priority = 1;  iconUrl = elem.attr("href");  break;
                    case "apple-touch-icon":
                    case "apple-touch-icon-precomposed":
                        priority = 2;  iconUrl = elem.attr("href");
                        size = parseSize(elem.attr("sizes").toLowerCase());  break;
                }
                if (priority!=0 && Util.isNotEmpty(iconUrl))  {
                    items.add(new SiteImageItem(size, priority, iconUrl));
                }
            }
            else if (elem.tagName().equals("meta"))  {
                if (elem.attr("property").equals("og:image") && !elem.attr("content").isEmpty())
                {
                    items.add(new SiteImageItem(-1, 3, elem.attr("content")));
                }
            }
            else if (elem.tagName().equals("base"))  {
                base = elem.attr("href");
                if (base.isEmpty())  base = url;
            }
        }

        //    append url base
        try  {  URL baseURL = new URL(base);
                for (SiteImageItem item : items)  item.url = new URL(baseURL, item.url).toString();  }
        catch (MalformedURLException e)  {}  //ignore malformed base URLs

        //    order images by size, load images without sizes
        SiteImages siteImages = new SiteImages();
        for (SiteImageItem item : items)
            if (item.size!=-1)  siteImages.add(item);
            else  loadImage(siteImages, item);

        return siteImages;
    }

    // parse strings like '32x32'
    public static int parseSize(String value)  {
        int x=0;
        while (x!=value.length() && value.charAt(x)>='0' && value.charAt(x)<='9')  x++;
        if (x==0 || x+1>=value.length() || value.charAt(x)!='x')  return -1;
        String v1 = value.substring(0, x);
        String v2 = value.substring(x+1);
        if (!v1.equals(v2))  return -1;
        int result = Integer.parseInt(v1);
        return result<=0 || result>=32*1024 ? -1 : result;
    }

    public static void loadImage(SiteImages items, SiteImageItem item)
    {
        try
        {
            //    read content
            HttpURLConnection conn = null;
            byte[] content;
            try  {
                conn = (HttpURLConnection) new URL (item.url).openConnection();
                content = ByteArray.read(conn.getInputStream());
            }
            finally  {  if (conn!=null)  conn.disconnect();  }

            //    try parse image with jdk first
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));
            if (image!=null)  {
                checkAndAddImage(items, image, item.priority, item.url);
                return;
            }

            //    parse with image4j if no support with jdk
            java.util.List<BufferedImage> images = ICODecoder.read(new ByteArrayInputStream(content));
            if (images.size()!=0)  {
                for (BufferedImage iconImage : images)  checkAndAddImage(items, iconImage, item.priority, item.url);
                return;
            }

            //    unsupported image
            throw new ExternalException("Unsupported image: "+item.url);
        }
        catch (Exception e)  {
            // TODO debug load error reason
            System.out.println("Can't load "+item.url+": "+e.toString());
        }
    }

    private static void checkAndAddImage(SiteImages items, BufferedImage image,
                                         int priority, String url) throws ExternalException  {
        if (image.getWidth()!=image.getHeight())  throw new ExternalException(
                "Image has different sizes: "+image.getWidth()+"x"+image.getHeight());
        SiteImageItem item = new SiteImageItem(image.getWidth(), priority, url);
        item.image = image;
        items.add(item);
    }
}
