import getfavicon.Servlet;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * User: And390
 * Date: 14.06.15
 * Time: 23:02
 */
public class TestGetFavicon
{

    @Test
    public void parseSize()  {
        assertEquals("", -1, Servlet.parseSize(""));
        assertEquals("", -1, Servlet.parseSize("?"));
        assertEquals("", -1, Servlet.parseSize("x"));
        assertEquals("", -1, Servlet.parseSize("2x"));
        assertEquals("", -1, Servlet.parseSize("x2"));
        assertEquals("", 2, Servlet.parseSize("2x2"));
        assertEquals("", -1, Servlet.parseSize("4x2"));
        assertEquals("", -1, Servlet.parseSize("0x0"));
        assertEquals("", 225, Servlet.parseSize("225x225"));
    }

    @Test
    public void isMoreAppropriate()  {
        // ���� � ������ �������� �������, �� �������� ������ � ������� ����������� � ����� �� ��������
        assertEquals("", false, Servlet.isMoreAppropriate(64, 64, 2, 64, 1));
        assertEquals("", false, Servlet.isMoreAppropriate(64, 64, 2, 64, 2));
        assertEquals("", true , Servlet.isMoreAppropriate(64, 64, 2, 64, 3));
        assertEquals("", false, Servlet.isMoreAppropriate(64, 64, 2, 128, 3));
        assertEquals("", false, Servlet.isMoreAppropriate(64, 64, 2, 32, 3));
        // ���� ������� � �������, �� �������� � ������� �������� ��� �����������
        assertEquals("", false, Servlet.isMoreAppropriate(64, 32, 2, 16, 2));
        assertEquals("", false, Servlet.isMoreAppropriate(64, 32, 2, 32, 2));
        assertEquals("", true , Servlet.isMoreAppropriate(64, 32, 2, 32, 3));
        assertEquals("", true , Servlet.isMoreAppropriate(64, 32, 2, 48, 1));
        assertEquals("", true , Servlet.isMoreAppropriate(64, 32, 2, 96, 1));
        // ���� ������� � �������, �� �������� � ������� ����������� ��� � ������� ��������, �� ������ ��������������
        assertEquals("", false, Servlet.isMoreAppropriate(64, 96, 2, 96, 1));
        assertEquals("", true , Servlet.isMoreAppropriate(64, 96, 2, 96, 3));
        assertEquals("", false, Servlet.isMoreAppropriate(64, 96, 2, 97, 3));
        assertEquals("", true , Servlet.isMoreAppropriate(64, 96, 2, 95, 1));
        assertEquals("", true , Servlet.isMoreAppropriate(64, 96, 2, 64, 1));
        assertEquals("", false, Servlet.isMoreAppropriate(64, 96, 2, 63, 1));
        // ��� ������� �������� ����� ���������
        assertEquals("", true , Servlet.isMoreAppropriate(64, 96, 2, 128, 1));
        assertEquals("", false, Servlet.isMoreAppropriate(64, 96, 2, 160, 1));
        assertEquals("", true , Servlet.isMoreAppropriate(64, 96, 2, 192, 1));
        assertEquals("", false, Servlet.isMoreAppropriate(64, 192, 2, 96, 3));
        assertEquals("", true , Servlet.isMoreAppropriate(64, 192, 2, 128, 3));
    }

}
