import getfavicon.Application;
import getfavicon.ServiceParser;
import org.junit.Test;
import utils.Util;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Map;

/**
 * And390 - 29.06.2015
 */
public class RunApplication
{
    @Test
    public void main() throws Exception
    {
        processAndWrite("vk.com");
    }

    @Test
    public void button() throws Exception
    {
        processAndWrite("metro.yandex.ru", 32, "png", true, "result.png");
    }

    @Test
    public void yandexMail() throws Exception
    {
        processAndWrite("http://mail.yandex.ru");
    }

    @Test
    public void directImage() throws Exception
    {
        processAndWrite("https://github.com/fluidicon.png");
    }

    @Test
    public void svg() throws Exception
    {
        processAndWrite("https://assets-cdn.github.com/pinned-octocat.svg");
    }

    @Test
    public void redirectDomain() throws Exception
    {
        processAndWrite("https://yastatic.net");
    }

    @Test
    public void getIconImages() throws Exception
    {
        Application.SiteImages images = Application.loadImages("http://vk.com");
        for (Application.SiteImageItem item : images)  System.out.println(item.toString());
    }

    @Test
    public void loadServiceImages() throws Exception
    {
        ServiceParser.loadServiceImages();

        File dir = new File("result");
        if (dir.exists())  Util.clearDir(dir);
        else  Util.createDir(dir);

        for (String provider : ServiceParser.services.keySet())  {
            Map<String, Application.ServiceImages> serviceImages = ServiceParser.services.get(provider);
            for (String service : serviceImages.keySet())  {
                processAndWrite(provider+"/"+service, 64, "png", true, "result/"+provider+"_"+service+".png");
            }
        }
    }

    @Test
    public void nonQuadSvg() throws Exception
    {
        Application.SiteImages images = ServiceParser.loadServiceImage("", "", "https://upload.wikimedia.org/wikipedia/commons/1/1b/EBay_logo.svg");
        try (FileOutputStream output = new FileOutputStream("result.png"))
        {  ImageIO.write(Application.drawSvg(images.get(0).diagram, 64), "PNG", output);  }
    }

    private static void processAndWrite(String url) throws Exception  {  processAndWrite(url, 32, "png", false, "result.png");  }

    private static void processAndWrite(String url, int size, String format, boolean button, String outputFileName) throws Exception
    {
        try (FileOutputStream output = new FileOutputStream(outputFileName))
        {  ImageIO.write(Application.process(new Application.Request(url), ""+size, format, button), format, output);  }
    }
}
