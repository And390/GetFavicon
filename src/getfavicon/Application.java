package getfavicon;

import com.google.common.cache.*;
import utils.ExternalException;
import utils.Util;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.TreeMap;
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

    public static class CachedIcon extends TreeMap<Integer, IconImageItem> {
        //TODO так хранить - расточительство
    }

    public static class IconImageItem  {
        int size;
        int priority;
        String url;
        BufferedImage image;  //null if not loaded
        public String toString()  {  return (size==0?"?":""+size)+"x"+(size==0?"?":""+size)+" "+priority+" "+url;  }
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
                return ImageProcessor.load(url);
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
                    ImageProcessor.loadImage(iconRecord, entry.getValue());
                }
            }
        }

        //    resize image
        if (image.getWidth()!=request.size)  image = ImageProcessor.getScaledImage(image, request.size, request.size);

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
            size==foundSize ? priority>foundPriority : foundSize==requestSize ? false :
            foundSize>requestSize ? size>=requestSize && (size%requestSize==0 && foundSize%requestSize!=0
                || size<foundSize && (size%requestSize==0 || foundSize%requestSize!=0)) :
            size>foundSize;
    }
}
