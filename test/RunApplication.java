import getfavicon.Application;
import getfavicon.ImageProcessor;
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
        BufferedImage result = Application.process(new Application.Request("vk.com"), "32", "png");
        try (FileOutputStream output = new FileOutputStream("result.png"))
        {  ImageIO.write(result, "PNG", output);  }
    }

    @Test
    public void getIconImages() throws Exception
    {
        Application.CachedIcon icon = ImageProcessor.load("http://vk.com");
        for (Application.IconImageItem item : icon.values())  System.out.println(item.toString());
    }

}
