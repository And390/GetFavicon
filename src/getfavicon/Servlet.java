package getfavicon;

import javax.imageio.ImageIO;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;

import static getfavicon.Application.Format;

/**
 * User: And390
 * Date: 08.06.15
 * Time: 0:43
 */
@WebServlet(urlPatterns={"/*"})
public class Servlet extends HttpServlet
{

    private static HashMap<Format, String> imageContentTypes = new HashMap<> ();
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
        //    log
        String q = request.getQueryString();
        System.out.println(url + (q!=null ? "?"+q : ""));

        //    try process
        Application.Request appRequest = new Application.Request(url);
        try  {
            BufferedImage image = Application.process(appRequest, request.getParameter("size"), request.getParameter("format"));

            //    write result image
            response.setStatus(200);
            response.setContentType(imageContentTypes.get(appRequest.format));
            ImageIO.write(image, appRequest.format.toString(), response.getOutputStream());
        }
        //    in case of error return 500 with error icon
        catch (Exception e)
        {
            e.printStackTrace();
            int statusCode = e instanceof BadRequestException ? 400 :
                             e instanceof NotFoundException ? 404 : 500;
            BufferedImage image = Application.processError(appRequest, ""+statusCode);
            response.setStatus(statusCode);
            response.setContentType(imageContentTypes.get(appRequest.format));
            ImageIO.write(image, appRequest.format.toString(), response.getOutputStream());
        }
    }
}
