import com.github.tomakehurst.wiremock.junit.WireMockRule;
import getfavicon.Application;
import org.junit.Rule;
import org.junit.Test;
import utils.ExternalException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.Assert.assertEquals;


/**
 * And390 - 05.07.2015
 */
public class TestApplication
{
    @Rule
    public WireMockRule wireMockRule = new WireMockRule(8089);

    @Test
    public void loadImages() throws IOException, ExternalException
    {
        stubFor(get(urlEqualTo("/")).willReturn(aResponse()
                .withBody(
                        "<html><head>" +
                        "<link rel=\"shortcut icon\" href=\"favicon.png\">\n" +
                        "<link rel=\"apple-touch-icon\" href=\"apple-touch-icon_76.png\" sizes=76x76>" +
                        "<link rel=\"apple-touch-icon\" href=\"apple-touch-icon_120.png\">" +
                        "<link rel=\"apple-touch-icon\" href=\"apple-touch-icon_152.png\" sizes=152x152>" +
                        "<link rel=\"apple-touch-icon\" href=\"apple-touch-icon_144.png\" sizes=144x144>" +
                        "<meta property=\"og:image\" content=\"og-image.png\">" +
                        // add broken tags
                        "<link>\n" +
                        "<link rel=\"\">\n" +
                        "<link rel=\"shortcut icon\">\n" +
                        "<link rel=\"shortcut icon\" href=\"\">\n" +
                        "<meta>" +
                        "<meta property=\"og:image\">" +
                        "<meta content=\"og-image_96.png\">" +
                        "</head><body></body></html>")));
        stubFor(get(urlEqualTo("/favicon.png")).willReturn(aResponse().withBody(getImageContent(32))));
        stubFor(get(urlEqualTo("/apple-touch-icon_76.png")).willReturn(aResponse().withBody(getImageContent(76))));
        stubFor(get(urlEqualTo("/apple-touch-icon_120.png")).willReturn(aResponse().withBody(getImageContent(120))));
        stubFor(get(urlEqualTo("/apple-touch-icon_144.png")).willReturn(aResponse().withBody(getImageContent(144))));
        stubFor(get(urlEqualTo("/apple-touch-icon_152.png")).willReturn(aResponse().withBody(getImageContent(128))));  //lie!
        stubFor(get(urlEqualTo("/og-image.png")).willReturn(aResponse().withBody(getImageContent(64))));
        stubFor(get(urlEqualTo("/og-image_96.png")).willReturn(aResponse().withBody(getImageContent(96))));

        Application.SiteImages images = Application.loadImages("http://127.0.0.1:8089/");
        System.out.println(images);

        Application.SiteImages expected = new Application.SiteImages ();
        expected.add(new Application.SiteImageItem(32, 1, "http://127.0.0.1:8089/favicon.png"));
        expected.add(new Application.SiteImageItem(64, 3, "http://127.0.0.1:8089/og-image.png"));
        expected.add(new Application.SiteImageItem(76, 2, "http://127.0.0.1:8089/apple-touch-icon_76.png"));
        expected.add(new Application.SiteImageItem(120, 2, "http://127.0.0.1:8089/apple-touch-icon_120.png"));
        expected.add(new Application.SiteImageItem(144, 2, "http://127.0.0.1:8089/apple-touch-icon_144.png"));
        expected.add(new Application.SiteImageItem(152, 2, "http://127.0.0.1:8089/apple-touch-icon_152.png"));
        assertEquals("", expected, images);

        //    try get image with lie size and see the difference
        Application.getAppropriateImage(images, 152);

        expected = new Application.SiteImages ();
        expected.add(new Application.SiteImageItem(32, 1, "http://127.0.0.1:8089/favicon.png"));
        expected.add(new Application.SiteImageItem(64, 3, "http://127.0.0.1:8089/og-image.png"));
        expected.add(new Application.SiteImageItem(76, 2, "http://127.0.0.1:8089/apple-touch-icon_76.png"));
        expected.add(new Application.SiteImageItem(120, 2, "http://127.0.0.1:8089/apple-touch-icon_120.png"));
        expected.add(new Application.SiteImageItem(128, 2, "http://127.0.0.1:8089/apple-touch-icon_152.png"));
        expected.add(new Application.SiteImageItem(144, 2, "http://127.0.0.1:8089/apple-touch-icon_144.png"));
        assertEquals("", expected, images);
    }

    private static byte[] getImageContent(int size) throws IOException  {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(size, size, BufferedImage.TYPE_BYTE_GRAY), "PNG", bytes);
        return bytes.toByteArray();
    }

    @Test
    public void addImageOrder()  {
        Application.SiteImages a = new Application.SiteImages ();
        a.add(new Application.SiteImageItem (10, 1, "xxx"));
        a.add(new Application.SiteImageItem (20, 1, "yyy"));

        Application.SiteImages b = new Application.SiteImages ();
        b.add(new Application.SiteImageItem (20, 1, "yyy"));
        b.add(new Application.SiteImageItem(10, 1, "xxx"));

        assertEquals("", a, b);
    }
}
