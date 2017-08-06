import getfavicon.Application;
import java.awt.Color;
import org.junit.Test;
import utils.RandomUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import static org.junit.Assert.assertEquals;

import static getfavicon.Application.SiteImageItem.UNKNOWN;

/**
 * User: And390
 * Date: 14.06.15
 * Time: 23:02
 */
public class TestImages
{

    @Test
    public void parseSize()  {
        assertEquals("", UNKNOWN, Application.parseSize(""));
        assertEquals("", UNKNOWN, Application.parseSize("?"));
        assertEquals("", UNKNOWN, Application.parseSize("x"));
        assertEquals("", UNKNOWN, Application.parseSize("2x"));
        assertEquals("", UNKNOWN, Application.parseSize("x2"));
        assertEquals("", 2, Application.parseSize("2x2"));
        assertEquals("", 0, Application.parseSize("4x2"));
        assertEquals("", 0, Application.parseSize("0x0"));
        assertEquals("", 225, Application.parseSize("225x225"));
    }

    @Test
    public void isMoreAppropriate()  {
        // если с нужным размером найдено, то прокатит только с большим приоритетом и таким же размером
        assertEquals("", false, Application.isMoreAppropriate(64, 64, 1, 64, 2));
        assertEquals("", false, Application.isMoreAppropriate(64, 64, 2, 64, 2));
        assertEquals("", true , Application.isMoreAppropriate(64, 64, 3, 64, 2));
        assertEquals("", false, Application.isMoreAppropriate(64, 64, 3, 128, 2));
        assertEquals("", false, Application.isMoreAppropriate(64, 64, 3, 32, 2));
        // если найдено с меньшим, то прокатит с большим размером или приоритетом
        assertEquals("", false, Application.isMoreAppropriate(64, 32, 2, 16, 2));
        assertEquals("", false, Application.isMoreAppropriate(64, 32, 2, 32, 2));
        assertEquals("", true , Application.isMoreAppropriate(64, 32, 3, 32, 2));
        assertEquals("", true , Application.isMoreAppropriate(64, 32, 1, 48, 2));
        assertEquals("", true , Application.isMoreAppropriate(64, 32, 1, 96, 2));
        // если найдено с большим, то прокатит с большим приоритетом или с меньшим размером, но больше запрашиваемого
        assertEquals("", false, Application.isMoreAppropriate(64, 96, 1, 96, 2));
        assertEquals("", true , Application.isMoreAppropriate(64, 96, 3, 96, 2));
        assertEquals("", false, Application.isMoreAppropriate(64, 96, 3, 97, 2));
        assertEquals("", true , Application.isMoreAppropriate(64, 96, 1, 95, 2));
        assertEquals("", true , Application.isMoreAppropriate(64, 96, 1, 64, 2));
        assertEquals("", false, Application.isMoreAppropriate(64, 96, 1, 63, 2));
        // для больших размеров важна кратность
        assertEquals("", true , Application.isMoreAppropriate(64, 96, 1, 128, 2));
        assertEquals("", false, Application.isMoreAppropriate(64, 96, 1, 160, 2));
        assertEquals("", true , Application.isMoreAppropriate(64, 96, 1, 192, 2));
        assertEquals("", false, Application.isMoreAppropriate(64, 192, 3, 96, 2));
        assertEquals("", true , Application.isMoreAppropriate(64, 192, 3, 128, 2));
    }

    @Test
    public void scaleImage()  {
        BufferedImage source = new BufferedImage (RandomUtil.random(1, 256), RandomUtil.random(1, 256), BufferedImage.TYPE_3BYTE_BGR);
        int width = RandomUtil.random(1, 256);
        int height = RandomUtil.random(1, 256);
        BufferedImage result = Application.getScaledImage(source, width, height);
        assertEquals("", width, result.getWidth());
        assertEquals("", height, result.getHeight());
    }

    @Test
    public void drawError() throws IOException {
        BufferedImage image = Application.drawText(32, "GET", "ICO", new Color(0, 155, 0));
        try (FileOutputStream out = new FileOutputStream("result.png")) {
            ImageIO.write(image, Application.Format.PNG.toString().toLowerCase(), out);
        }
    }

    @Test
    public void checkButtonShape() throws IOException {
        BufferedImage image = ImageIO.read(new File("test_data/instagram.png"));
        System.out.println( Application.checkButtonShape(image, image.getWidth()) );
    }
}
