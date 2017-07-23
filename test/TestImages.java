import getfavicon.Application;
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
        assertEquals("", -1, Application.parseSize(""));
        assertEquals("", -1, Application.parseSize("?"));
        assertEquals("", -1, Application.parseSize("x"));
        assertEquals("", -1, Application.parseSize("2x"));
        assertEquals("", -1, Application.parseSize("x2"));
        assertEquals("", 2, Application.parseSize("2x2"));
        assertEquals("", -1, Application.parseSize("4x2"));
        assertEquals("", -1, Application.parseSize("0x0"));
        assertEquals("", 225, Application.parseSize("225x225"));
    }

    @Test
    public void isMoreAppropriate()  {
        // ���� � ������ �������� �������, �� �������� ������ � ������� ����������� � ����� �� ��������
        assertEquals("", false, Application.isMoreAppropriate(64, 64, 1, 64, 2));
        assertEquals("", false, Application.isMoreAppropriate(64, 64, 2, 64, 2));
        assertEquals("", true , Application.isMoreAppropriate(64, 64, 3, 64, 2));
        assertEquals("", false, Application.isMoreAppropriate(64, 64, 3, 128, 2));
        assertEquals("", false, Application.isMoreAppropriate(64, 64, 3, 32, 2));
        // ���� ������� � �������, �� �������� � ������� �������� ��� �����������
        assertEquals("", false, Application.isMoreAppropriate(64, 32, 2, 16, 2));
        assertEquals("", false, Application.isMoreAppropriate(64, 32, 2, 32, 2));
        assertEquals("", true , Application.isMoreAppropriate(64, 32, 3, 32, 2));
        assertEquals("", true , Application.isMoreAppropriate(64, 32, 1, 48, 2));
        assertEquals("", true , Application.isMoreAppropriate(64, 32, 1, 96, 2));
        // ���� ������� � �������, �� �������� � ������� ����������� ��� � ������� ��������, �� ������ ��������������
        assertEquals("", false, Application.isMoreAppropriate(64, 96, 1, 96, 2));
        assertEquals("", true , Application.isMoreAppropriate(64, 96, 3, 96, 2));
        assertEquals("", false, Application.isMoreAppropriate(64, 96, 3, 97, 2));
        assertEquals("", true , Application.isMoreAppropriate(64, 96, 1, 95, 2));
        assertEquals("", true , Application.isMoreAppropriate(64, 96, 1, 64, 2));
        assertEquals("", false, Application.isMoreAppropriate(64, 96, 1, 63, 2));
        // ��� ������� �������� ����� ���������
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
        BufferedImage result = Application.getScaledImage(source, width, height, null);
        assertEquals("", width, result.getWidth());
        assertEquals("", height, result.getHeight());
    }
}
