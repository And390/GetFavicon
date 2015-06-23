package getfavicon;

import net.sf.image4j.codec.ico.ICODecoder;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import utils.ByteArray;
import utils.ExternalException;
import utils.Util;

import javax.imageio.ImageIO;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.List;

/**
 * User: And390
 * Date: 08.06.15
 * Time: 0:43
 */
@WebServlet(urlPatterns={"/*"})
public class Servlet extends HttpServlet
{
    /*
       1.
           ? HTTP: connection/socket timeout, User-Agent, Redirects, Referer
           + Искать наиболее подходящий размер (с учетом приоритетов)
           + Перекодировать размер и формат, возвращать соответствующий Content-Type
           + Генерировать иконку с ошибкой
           + рисовать различные коды 400, 404, 500
           + прикрутить image4j для поддержки ico файлов
             List<BufferedImage> image = ICODecoder.read(new File("input.ico"));
           + разбирать все иконки с сайта
           - кэш
       2.
           - разбить код на логические части: работа с изображениями, парсинг хтмл
           - выбрать шрифт (положить, если нестандартный), установить правильную выоту и descending
           - Дебаг режим тоже бы не помешал
     */


    static enum Format { PNG, ICO, GIF, BMP, JPG };

    static HashMap<Format, String> imageContentTypes = new HashMap<> ();
    static  {
        imageContentTypes.put(Format.PNG, "image/png");
        imageContentTypes.put(Format.ICO, "image/x-icon");
        imageContentTypes.put(Format.GIF, "image/gif");
        imageContentTypes.put(Format.BMP, "image/bmp");
        imageContentTypes.put(Format.JPG, "image/jpeg");
    }

    @Override
    public void init() throws ServletException {

    }

    private static void close(Closeable closeable)  {
        if (closeable!=null)  try  {  closeable.close();  }  catch (IOException e)  {}  //TODO log
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
    {
        //    get input url
        String url = request.getRequestURI();
        url = url.substring(request.getContextPath().length());  //cut context
        if (url.startsWith("/"))  url = url.substring(1);
        System.out.println(url);  //log
        //    add default protocol
        int i = url.indexOf("://");
        if (i==-1 || url.lastIndexOf(":", i-1)!=-1 || url.lastIndexOf("/", i-1)!=-1) {
            url = "http://" + url;
        }

        //
        int requestSize = 16;
        Format format = Format.PNG;
        try
        {
            //    get parameters
            try  {
                String requestSize_ = request.getParameter("size");
                if (Util.isNotEmpty(requestSize_))  requestSize = Util.getInt(requestSize_, "size", 1, 256);
                String format_ = request.getParameter("format");
                if (Util.isNotEmpty(format_))  format = Format.valueOf(format_.toUpperCase());
            }
            catch (ExternalException e)  {  throw new BadRequestException (e.getMessage());  }

            //    execute
            Document document = Jsoup.parse(new URL (url), 15000);

            //    parse HTML
            List<IconImageItem> items = new ArrayList<>();
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

            //    order images by size, load images without sizes
            TreeMap<Integer, IconImageItem> itemsBySize = new TreeMap<>();
            for (Iterator<IconImageItem> iter=items.iterator(); iter.hasNext(); )  {
                IconImageItem item = iter.next();
                if (item.size!=0)  {
                    itemsBySize.put(item.size, item);
                }
                else  {
                    loadImage(itemsBySize, item, base);
                }
            }

            //    find image
            BufferedImage image;
            while (true)  {
                if (itemsBySize.isEmpty())  throw new NotFoundException();

                Map.Entry<Integer, IconImageItem> entry = itemsBySize.ceilingEntry(requestSize);
                if (entry!=null)  {
                    //  if size is not multiple of requested size then try to find it bigger
                    if (entry.getKey()%requestSize != 0)
                        for (Map.Entry<Integer, IconImageItem> entry_=entry; entry_!=null; entry_=itemsBySize.higherEntry(entry_.getKey()))
                            if (entry_.getKey()%requestSize == 0)  {  entry = entry_;  break;  }
                }
                else  {
                    entry = itemsBySize.lastEntry();
                }

                if (entry.getValue().image!=null)  {
                    image = entry.getValue().image;
                    break;
                }
                else  {
                    //    load if need
                    itemsBySize.remove(entry.getKey());
                    loadImage(itemsBySize, entry.getValue(), base);
                }
            }

            //    resize image
            if (image.getWidth()!=requestSize)  image = getScaledImage(image, requestSize, requestSize);

            //
            response.setStatus(200);
            response.setContentType(imageContentTypes.get(format));
            ImageIO.write(image, format.toString(), response.getOutputStream());
        }
        catch (Exception e)
        {
            e.printStackTrace();
            //    in case of error return 500 with error icon
            int statusCode = e instanceof BadRequestException ? 400 :
                             e instanceof NotFoundException ? 404 : 500;
            String status = ""+statusCode;
            BufferedImage image = new BufferedImage(requestSize, requestSize, BufferedImage.TYPE_INT_RGB);
            Graphics g = image.createGraphics();
            g.setColor(Color.RED);
            g.setFont(new Font("Lucida Sans TypeWriter", Font.PLAIN, requestSize / 2));
            FontMetrics fontMetrics = g.getFontMetrics();
            g.drawString("ERR", requestSize/2 - fontMetrics.stringWidth("ERR")/2, requestSize/2);
            g.drawString(status, requestSize/2 - fontMetrics.stringWidth(status)/2, requestSize);
            response.setStatus(statusCode);
            response.setContentType(imageContentTypes.get(format));
            ImageIO.write(image, format.toString(), response.getOutputStream());
        }
    }

    static class IconImageItem  {
        int size;
        int priority;
        String url;
        BufferedImage image;
        public String toString()  {  return size+"x"+size+" "+priority+" "+url;  }
    }

    private static void loadImage(TreeMap<Integer, IconImageItem> items, IconImageItem item, String base)
            throws IOException
    {
        try
        {
            //    read content
            HttpURLConnection conn = null;
            byte[] content;
            try  {
                conn = (HttpURLConnection) new URL (new URL (base), item.url).openConnection();
                content = ByteArray.read(conn.getInputStream());
            }
            finally  {
                if (conn!=null)  conn.disconnect();
            }

            //    try parse image with jdk first
            BufferedImage image = ImageIO.read(new ByteArrayInputStream (content));
            if (image!=null)  {
                checkAndAddImage(items, image, item.priority, item.url);
                return;
            }

            //    parse with image4j if no support with jdk
            List<BufferedImage> images = ICODecoder.read(new ByteArrayInputStream (content));
            if (images.size()!=0)  {
                for (BufferedImage iconImage : images)  checkAndAddImage(items, iconImage, item.priority, item.url);
                return;
            }

            //    unsupported image
            throw new ExternalException("Unsupported image: "+item.url);
        }
        catch (Exception e)  {
            // TODO debug load error reason
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
        return;
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

    // ищется ссылка с размером, равным запрашиваемому, если нет, то ближайшая с большим размером и кратным запрашиваемому,
    // если нет, то просто ближайшая с большим, если нет, то ближайшая с меньшим
    // с равным размером выбирается ссылка с наибольшим приоритетом
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
