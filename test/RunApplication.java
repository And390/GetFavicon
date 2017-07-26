import getfavicon.Application;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.FileOutputStream;

/**
 * And390 - 29.06.2015
 */
public class RunApplication
{
    @Test
    public void main() throws Exception
    {
        BufferedImage result = Application.process(new Application.Request("vk.com"), "32", "png", false);
        try (FileOutputStream output = new FileOutputStream("result.png"))
        {  ImageIO.write(result, "PNG", output);  }
    }

    @Test
    public void button() throws Exception
    {
        BufferedImage result = Application.process(new Application.Request("metro.yandex.ru"), "32", "png", true);
        try (FileOutputStream output = new FileOutputStream("result.png"))
        {  ImageIO.write(result, "PNG", output);  }
    }

    @Test
    public void yandexMail() throws Exception
    {
        BufferedImage result = Application.process(new Application.Request("mail.yandex.ru"), "32", "png", true);
        try (FileOutputStream output = new FileOutputStream("result.png"))
        {  ImageIO.write(result, "PNG", output);  }
    }

    @Test
    public void directImage() throws Exception
    {
        BufferedImage result = Application.process(new Application.Request("https://github.com/fluidicon.png"), "32", "png", true);
        try (FileOutputStream output = new FileOutputStream("result.png"))
        {  ImageIO.write(result, "PNG", output);  }
    }

    @Test
    public void svg() throws Exception
    {
        BufferedImage result = Application.process(new Application.Request("https://assets-cdn.github.com/pinned-octocat.svg"), "256", "png", false);
        try (FileOutputStream output = new FileOutputStream("result.png"))
        {  ImageIO.write(result, "PNG", output);  }
    }

    @Test
    public void getIconImages() throws Exception
    {
        Application.SiteImages images = Application.loadImages("http://vk.com");
        for (Application.SiteImageItem item : images)  System.out.println(item.toString());
    }
}
