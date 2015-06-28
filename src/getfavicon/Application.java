package getfavicon;

import com.google.common.cache.*;
import net.sf.image4j.codec.ico.ICODecoder;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import utils.ByteArray;
import utils.ExternalException;
import utils.Util;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
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

    public static enum Format { PNG, ICO, GIF, BMP, JPG }

    public static class Request {
        public String url;
        public int size = 16;
        public Format format = Format.PNG;
        public Request(String url_)  {  url=url_;  }
    }

    private static class CachedIcon extends TreeMap<Integer, IconImageItem> {
        //TODO так хранить - расточительство
    }

    private static class IconImageItem  {
        int size;
        int priority;
        String url;
        BufferedImage image;  //null if not loaded
        public String toString()  {  return size+"x"+size+" "+priority+" "+url;  }
    }

    private static LoadingCache<String, CachedIcon> cache = CacheBuilder.newBuilder()
        .maximumSize(10000)    // максимальное количество элементов в кэше
        .concurrencyLevel(1)  // следует установить равным количеству обрабатывающих потоков, что зависит от настройки сервера
        .expireAfterAccess(1, TimeUnit.DAYS)  // через день данные удал€ютс€ из кэша и сохран€ютс€ в базу
        .removalListener(new RemovalListener<String, CachedIcon>() {
            public void onRemoval(RemovalNotification<String, CachedIcon> removalNotification) {

            }
        })
        .build(new CacheLoader<String, CachedIcon>() {
            // загрузка отсутствующего элемента в кэше
            public CachedIcon load(String url) throws Exception {
                return Application.load(url);
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
            if (Util.isNotEmpty(requestFormat))  request.format = Format.valueOf(requestFormat.toUpperCase());
        }
        catch (ExternalException|MalformedURLException e)  {  throw new BadRequestException (e.getMessage());  }

        //    get icon images from cache or load
        CachedIcon iconRecord;
        try  {  iconRecord = cache.get(request.url);  }
        catch (ExecutionException e)  {
            if (e.getCause() instanceof IOException)  throw new ExternalException (e.getCause());
            else  throw new RuntimeException (e.getCause());
        }

        //    find image
        BufferedImage image;
        synchronized (iconRecord)  {  //todo плохо, что при загрузке доп. изображений блокируетс€ доступ на чтение к уже загруженным
            while (true)  {
                if (iconRecord.isEmpty())  throw new NotFoundException();

                //    try get bigger size
                Map.Entry<Integer, IconImageItem> entry = iconRecord.ceilingEntry(request.size);
                if (entry!=null)  {
                    //  if size is not multiple of requested size then try to find it bigger
                    if (entry.getKey()%request.size != 0)
                        for (Map.Entry<Integer, IconImageItem> entry_=entry; entry_!=null; entry_=iconRecord.higherEntry(entry_.getKey()))
                            if (entry_.getKey()%request.size == 0)  {  entry = entry_;  break;  }
                }
                //    if no, get lower
                else  {
                    entry = iconRecord.floorEntry(request.size);
                }

                //    return if loaded or else load and refind
                image = entry.getValue().image;
                if (image!=null)  {
                    break;
                }
                else  {
                    iconRecord.remove(entry.getKey());
                    loadImage(iconRecord, entry.getValue());
                }
            }
        }

        //    resize image
        if (image.getWidth()!=request.size)  image = getScaledImage(image, request.size, request.size);

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

    private static CachedIcon load(String url) throws IOException {
        //    execute
        Document document = Jsoup.parse(new URL(url), 15000);

        //    parse HTML
        java.util.List<IconImageItem> items = new ArrayList<>();
        String base = url;
        for (Element elem : document.head().children())  {
            if (elem.tagName().equals("link"))  {
                int priority = 0;
                String iconUrl = null;
                int size = 0;
                switch (elem.attr("rel").toLowerCase())  {
                    case "shortcut icon":  priority = 2;  iconUrl = elem.attr("href").toLowerCase();  break;
                    case "apple-touch-icon":  priority = 1;  iconUrl = elem.attr("href").toLowerCase();
                                              size = parseSize(elem.attr("sizes").toLowerCase());  break;
                }
                if (priority!=0)  {
                    IconImageItem item = new IconImageItem();
                    item.size = size;
                    item.priority = priority;
                    item.url = iconUrl;
                    items.add(item);
                }
            }
            else if (elem.tagName().equals("base"))  {
                base = elem.attr("href");
                if (!base.isEmpty())  base = url;
            }
        }

        //    append url base
        URL baseURL = new URL(base);
        for (IconImageItem item : items)  item.url = new URL(baseURL, item.url).toString();

        //    order images by size, load images without sizes
        CachedIcon itemsBySize = new CachedIcon ();
        for (Iterator<IconImageItem> iter=items.iterator(); iter.hasNext(); )  {
            IconImageItem item = iter.next();
            if (item.size!=0)  {
                itemsBySize.put(item.size, item);
            }
            else  {
                loadImage(itemsBySize, item);
            }
        }

        return itemsBySize;
    }

    private static void loadImage(TreeMap<Integer, IconImageItem> items, IconImageItem item)
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
            List<BufferedImage> images = ICODecoder.read(new ByteArrayInputStream(content));
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

    private static void checkAndAddImage(TreeMap<Integer, IconImageItem> items, BufferedImage image,
                                         int priority, String url) throws ExternalException  {
        if (image.getWidth()!=image.getHeight())  throw new ExternalException(
                "Image has different sizes: "+image.getWidth()+"x"+image.getHeight());
        IconImageItem item = new IconImageItem ();
        item.image = image;
        item.size = image.getWidth();
        item.priority = priority;
        item.url = url;
        IconImageItem other = items.put(item.size, item);
        if (other!=null && other.priority>=item.priority)  items.put(item.size, other);
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

    // ищетс€ ссылка с размером, равным запрашиваемому, если нет, то ближайша€ с большим размером и кратным запрашиваемому,
    // если нет, то просто ближайша€ с большим, если нет, то ближайша€ с меньшим
    // с равным размером выбираетс€ ссылка с наибольшим приоритетом
    public static boolean isMoreAppropriate(int requestSize, int foundSize, int foundPriority, int size, int priority)  {
        return
            size==foundSize ? priority>foundPriority : foundSize==requestSize ? false :
            foundSize>requestSize ? size>=requestSize && (size%requestSize==0 && foundSize%requestSize!=0
                || size<foundSize && (size%requestSize==0 || foundSize%requestSize!=0)) :
            size>foundSize;
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
}
