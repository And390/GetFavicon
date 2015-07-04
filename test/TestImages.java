import getfavicon.Application;
import getfavicon.ImageProcessor;
import org.junit.Test;
import utils.RandomUtil;

import java.awt.image.BufferedImage;

import static org.junit.Assert.assertEquals;

/**
 * User: And390
 * Date: 14.06.15
 * Time: 23:02
 */
public class TestImages
{

    @Test
    public void parseSize()  {
        assertEquals("", -1, ImageProcessor.parseSize(""));
        assertEquals("", -1, ImageProcessor.parseSize("?"));
        assertEquals("", -1, ImageProcessor.parseSize("x"));
        assertEquals("", -1, ImageProcessor.parseSize("2x"));
        assertEquals("", -1, ImageProcessor.parseSize("x2"));
        assertEquals("", 2, ImageProcessor.parseSize("2x2"));
        assertEquals("", -1, ImageProcessor.parseSize("4x2"));
        assertEquals("", -1, ImageProcessor.parseSize("0x0"));
        assertEquals("", 225, ImageProcessor.parseSize("225x225"));
    }

    @Test
    public void isMoreAppropriate()  {
        // ���� � ������ �������� �������, �� �������� ������ � ������� ����������� � ����� �� ��������
        assertEquals("", false, Application.isMoreAppropriate(64, 64, 2, 64, 1));
        assertEquals("", false, Application.isMoreAppropriate(64, 64, 2, 64, 2));
        assertEquals("", true , Application.isMoreAppropriate(64, 64, 2, 64, 3));
        assertEquals("", false, Application.isMoreAppropriate(64, 64, 2, 128, 3));
        assertEquals("", false, Application.isMoreAppropriate(64, 64, 2, 32, 3));
        // ���� ������� � �������, �� �������� � ������� �������� ��� �����������
        assertEquals("", false, Application.isMoreAppropriate(64, 32, 2, 16, 2));
        assertEquals("", false, Application.isMoreAppropriate(64, 32, 2, 32, 2));
        assertEquals("", true , Application.isMoreAppropriate(64, 32, 2, 32, 3));
        assertEquals("", true , Application.isMoreAppropriate(64, 32, 2, 48, 1));
        assertEquals("", true , Application.isMoreAppropriate(64, 32, 2, 96, 1));
        // ���� ������� � �������, �� �������� � ������� ����������� ��� � ������� ��������, �� ������ ��������������
        assertEquals("", false, Application.isMoreAppropriate(64, 96, 2, 96, 1));
        assertEquals("", true , Application.isMoreAppropriate(64, 96, 2, 96, 3));
        assertEquals("", false, Application.isMoreAppropriate(64, 96, 2, 97, 3));
        assertEquals("", true , Application.isMoreAppropriate(64, 96, 2, 95, 1));
        assertEquals("", true , Application.isMoreAppropriate(64, 96, 2, 64, 1));
        assertEquals("", false, Application.isMoreAppropriate(64, 96, 2, 63, 1));
        // ��� ������� �������� ����� ���������
        assertEquals("", true , Application.isMoreAppropriate(64, 96, 2, 128, 1));
        assertEquals("", false, Application.isMoreAppropriate(64, 96, 2, 160, 1));
        assertEquals("", true , Application.isMoreAppropriate(64, 96, 2, 192, 1));
        assertEquals("", false, Application.isMoreAppropriate(64, 192, 2, 96, 3));
        assertEquals("", true , Application.isMoreAppropriate(64, 192, 2, 128, 3));
    }

    @Test
    public void scaleImage()  {
        BufferedImage source = new BufferedImage (RandomUtil.random(1, 256), RandomUtil.random(1, 256), BufferedImage.TYPE_3BYTE_BGR);
        int width = RandomUtil.random(1, 256);
        int height = RandomUtil.random(1, 256);
        BufferedImage result = ImageProcessor.getScaledImage(source, width, height);
        assertEquals("", width, result.getWidth());
        assertEquals("", height, result.getHeight());
    }
}
