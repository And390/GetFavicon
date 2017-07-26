import getfavicon.Application;
import org.junit.Test;
import utils.Util;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * And390 - 23.07.2017
 */
public class TestImageProcess
{
    @Test
    public void replaceWhiteBackgroundWithAlpha() throws IOException
    {
        convert("test_data/yandex.mail.jpg");
        convert("test_data/yandex.maps.png");
    }

    private static void convert(String fileName) throws IOException {
        BufferedImage img = ImageIO.read(new File(fileName));
        img = Application.getScaledImage(img, img.getWidth(), img.getHeight());
        Application.replaceWhiteBackgroundWithAlpha(img);
        ImageIO.write(img, "png", new File(Util.addFileName(fileName, "_result", true)));
    }
}
