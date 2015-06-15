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
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;

/**
 * User: And390
 * Date: 08.06.15
 * Time: 0:43
 */
@WebServlet(urlPatterns={"/*"})
public class GetFaviconServlet extends HttpServlet
{
    /*
       ? HTTP: connection/socket timeout, User-Agent, Redirects, Referer
       + Искать наиболее подходящий размер (с учетом приоритетов)
       + Перекодировать размер и формат, возвращать соответствующий Content-Type
       - Генерировать иконку с ошибкой
       - Дебаг режим тоже бы не помешал
       - прикрутить image4j для поддержки ico файлов
         List<BufferedImage> image = ICODecoder.read(new File("input.ico"));
       - разбирать все иконки с сайта и запоминать их
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
            String requestSize_ = request.getParameter("size");
            if (Util.isNotEmpty(requestSize_))  requestSize = Util.getInt(requestSize_, "size", 1, 256);
            String format_ = request.getParameter("format");
            if (Util.isNotEmpty(format_))  format = Format.valueOf(format_.toUpperCase());

            //    execute
            Document document = Jsoup.parse(new URL (url), 15000);

            //    found
            String foundIconUrl = null;
            int foundPriority = 0;
            int foundSize = 0;
            String base = url;
            for (Element elem : document.head().children()) {
                if (elem.tagName().equals("link")) {
                    int priority = 0;
                    String iconUrl = null;
                    int size = 0;
                    switch (elem.attr("rel").toLowerCase()) {
                        case "shortcut icon":  priority = 2;  iconUrl = elem.attr("href").toLowerCase();  break;
                        case "apple-touch-icon":  priority = 1;  iconUrl = elem.attr("href").toLowerCase();
                                                  size = parseSize(elem.attr("sizes").toLowerCase());  break;
                    }
                    if (isMoreAppropriate(requestSize, foundSize, foundPriority, size, priority))
                    {  foundPriority=priority;  foundIconUrl=iconUrl;  foundSize=size;  }
                }
                else if (elem.tagName().equals("base")){
                    base = elem.attr("href");
                    if (!base.isEmpty())  base = url;
                }
            }
            if (foundIconUrl==null)  throw new ExternalException ("icon not found");

            //    load image
            BufferedImage image;
            //String contentType;
            HttpURLConnection conn = (HttpURLConnection) new URL (new URL (base), foundIconUrl).openConnection();
            try {
                //contentType = conn.getContentType();
                image = ImageIO.read(conn.getInputStream());
                if (image==null)  throw new ExternalException ("unsupported format");
                if (image.getWidth()!=requestSize || image.getHeight()!=requestSize)  {
                    image = getScaledImage(image, requestSize, requestSize);
                }
            }
            finally {
                conn.disconnect();
            }

            //
            response.setStatus(200);
            response.setContentType(imageContentTypes.get(format));
            ImageIO.write(image, format.toString(), response.getOutputStream());
        }
        catch (Exception e)
        {
            e.printStackTrace();
            //    in case of error return 500 with error icon
            byte[] respBytes = ByteArray.read(getServletContext().getResourceAsStream("500_16.png"));
            response.setStatus(500);
            response.setContentType("image/png");
            response.getOutputStream().write(respBytes);
        }
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
