import getfavicon.Application;
import org.junit.*;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.FileOutputStream;
import java.io.IOException;

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
        Application.CachedIcon icon = Application.load("http://vk.com");
        for (Application.IconImageItem item : icon.values())  System.out.println(item.toString());
    }

}
