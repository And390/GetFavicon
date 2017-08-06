import getfavicon.Application;
import org.junit.Test;

public class TestLoad
{
    // png in .ico
    @Test
    public void testBing() {
        String url = "http://www.bing.com/sa/simg/bing_p_rr_teal_min.ico";
        Application.SiteImages images = new Application.SiteImages();
        Application.SiteImageItem item = new Application.SiteImageItem(Application.SiteImageItem.UNKNOWN, 1, url);
        Application.loadImage(Application.getConnection(url), images, item);
    }

}
