package getfavicon;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.sf.image4j.codec.ico.ICOEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.ByteArray;
import utils.ExternalException;

import javax.imageio.ImageIO;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static getfavicon.Application.Format;

/**
 * User: And390
 * Date: 08.06.15
 * Time: 0:43
 */
@WebServlet(urlPatterns={"/*"})
public class Servlet extends HttpServlet
{
    private static Logger log = LoggerFactory.getLogger(Servlet.class);

    private static final HashMap<Format, String> imageContentTypes = new HashMap<> ();
    static  {
        imageContentTypes.put(Format.PNG, "image/png");
        imageContentTypes.put(Format.ICO, "image/x-icon");
        imageContentTypes.put(Format.GIF, "image/gif");
        imageContentTypes.put(Format.BMP, "image/bmp");
        imageContentTypes.put(Format.JPEG, "image/jpeg");
    }

    private static final String DEFAULT_CACHE_CONTROL = "max-age=" + (3600 * 24 * 7);

    private byte[] mainPage;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void init() throws ServletException {
        try  {
            ServletContext context = getServletContext();
            mainPage = ByteArray.read(context.getResourceAsStream("/index.html"));
            ServiceParser.loadServiceImages();
        }
        catch (IOException|ExternalException e)  {  throw new ServletException(e);  }
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
        log.info("request: " + url + (q!=null ? "?"+q : ""));

        //    default page
        if (url.isEmpty() || url.equals("index.html") || url.equals("index.htm"))  {
            response.setContentType("text/html; charset=ASCII");
            response.getOutputStream().write(mainPage);
            return;
        }

        //    favicon
        if (url.equals("favicon.ico"))  {
            BufferedImage image = Application.drawText(32, "GET", "ICO", new java.awt.Color(0, 155, 0));
            response.setContentType(imageContentTypes.get(Format.PNG));
            ImageIO.write(image, Format.PNG.toString(), response.getOutputStream());
            return;
        }

        //    services api point
        if (url.equals("services") || url.equals("services/"))  {
            List<ProviderWrapper> result = new ArrayList<>();
            for (String providerCode : ServiceParser.services.keySet())  {
                Map<String, Application.ServiceImages> providerServices = ServiceParser.services.get(providerCode);
                ProviderWrapper provider = new ProviderWrapper(providerCode, ServiceParser.providerNames.get(providerCode));
                for (String service : providerServices.keySet())
                    provider.services.add(new ServiceWrapper(service, providerServices.get(service)));
                result.add(provider);
            }

            response.setHeader("Access-Control-Allow-Origin", "*");
            response.setContentType("application/json; charset=UTF-8");
            try  {  objectMapper.writeValue(response.getOutputStream(), result);  }
            catch (JsonProcessingException e)  {
                e.printStackTrace();
                response.setStatus(500);
                response.setContentType("plain/text; charset=UTF-8");
                response.getOutputStream().write("Internal Server Error".getBytes("UTF-8"));
            }
            return;
        }

        //    try process
        Application.Request appRequest = new Application.Request(url);
        try  {
            BufferedImage image = Application.process(appRequest, request.getParameter("size"), request.getParameter("format"), request.getParameter("button") != null);

            //    write result image
            response.setStatus(200);
            response.setContentType(imageContentTypes.get(appRequest.format));
            response.setHeader("Cache-Control", request.getParameter("no-store") != null ? "no-store" :
                                                request.getParameter("max-age") != null ? "max-age=" + request.getParameter("max-age") : DEFAULT_CACHE_CONTROL);
            OutputStream out = response.getOutputStream();
            if (appRequest.format==Format.ICO)  ICOEncoder.write(image, out);
            else if (!ImageIO.write(image, appRequest.format.toString(), out))
                throw new BadRequestException("Can't write " + appRequest.format + " for this image");
            out.flush();
        }
        //    in case of error return 500 with error icon
        catch (Exception e)
        {
            int statusCode = e instanceof BadRequestException ? 400 :
                             e instanceof NotFoundException ? 404 : 500;
            if (statusCode==500)  e.printStackTrace();
            BufferedImage image = Application.drawError(appRequest.size, ""+statusCode);
            response.setStatus(statusCode);
            response.setContentType(imageContentTypes.get(appRequest.format));
            ImageIO.write(image, appRequest.format.toString(), response.getOutputStream());
        }
    }

    public static class ProviderWrapper
    {
        public String code;
        public String name;
        public List<ServiceWrapper> services = new ArrayList<>();
        public ProviderWrapper(String code_, String name_)  {  code = code_;  name = name_;  }
    }

    public static class ServiceWrapper
    {
        public String code;
        public String name;
        public String url;

        public ServiceWrapper(String code_, Application.ServiceImages source)  {
            code = code_;
            name = source.name;
            url = source.url;
        }
    }
}
