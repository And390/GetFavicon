package getfavicon;

import getfavicon.Application.CachedIcon;
import getfavicon.Application.IconImageItem;
import net.sf.image4j.codec.ico.ICODecoder;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import utils.ByteArray;
import utils.ExternalException;

import javax.imageio.ImageIO;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;

/**
 * And390 - 05.07.2015
 */
public class ImageProcessor
{
    public static CachedIcon load(String url) throws IOException {
        //    execute
        Document document = Jsoup.connect(url)
           .ignoreContentType(true)
           .userAgent("Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:38.0) Gecko/20100101 Firefox/38.0")
           .referrer("http://www.google.com")
           .timeout(12000)
           //.followRedirects(true)
           .get();

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
                if (base.isEmpty())  base = url;
            }
        }

        //    append url base
        try  {  URL baseURL = new URL(base);
                for (IconImageItem item : items)  item.url = new URL(baseURL, item.url).toString();  }
        catch (MalformedURLException e)  {}  //ignore malformed base URLs

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

    public static void loadImage(TreeMap<Integer, IconImageItem> items, IconImageItem item)
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
