package getfavicon;

import utils.ExternalException;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;

/**
 * User: And390
 * Date: 17.06.15
 * Time: 0:19
 */
public class Application
{

    /*
    public static enum Format { PNG, ICO, GIF, BMP, JPG };

    public static void process(String url, String requestFormat, String requestSize, ProcessHandler handler)
            throws ExternalException
    {
        int size;
        Format format;
        int status;
        BufferedImage image;

        try
        {
            //    add default protocol to URL
            int i = url.indexOf("://");
            if (i==-1 || url.lastIndexOf(":", i-1)!=-1 || url.lastIndexOf("/", i-1)!=-1) {
                url = "http://" + url;
            }
        }
        catch (Exception e)
        {
            if (e instanceof ExternalException)  System.err.println(e.toString());
            else  e.printStackTrace();
            status = e instanceof Application.BadRequestException ? 400 :
                     e instanceof Application.NotFoundException ? 404 : 500;
            image = makeErrorIcon(size, status, e);
        }

        ImageIO.write(image, format.toString(), handler.apply(status, format));
    }

    public static BufferedImage makeErrorIcon(int size, String status, Exception e) throws IOException
    {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics g = image.createGraphics();
        g.setColor(Color.RED);
        g.setFont(new Font("Lucida Sans TypeWriter", Font.PLAIN, size / 2));
        FontMetrics fontMetrics = g.getFontMetrics();
        g.drawString("ERR", size/2 - fontMetrics.stringWidth("ERR")/2, size/2);
        g.drawString(status, size/2 - fontMetrics.stringWidth(status)/2, size);
        return image;
    }

    public interface ProcessHandler  {
        OutputStream apply(int status, Format format);
    }
    */
}
