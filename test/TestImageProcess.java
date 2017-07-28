import com.kitfox.svg.SVGDiagram;
import com.kitfox.svg.SVGUniverse;
import getfavicon.Application;
import org.junit.Test;
import utils.ByteArray;
import utils.ExternalException;
import utils.Util;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;

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

    @Test
    public void drawSVG() throws Exception
    {
        byte[] content = ByteArray.read("test_data/test2.svg");
        try (FileOutputStream output = new FileOutputStream("result.png"))  {
            SVGUniverse universe = new SVGUniverse();
            SVGDiagram diagram = universe.getDiagram(universe.loadSVG(new ByteArrayInputStream(content), "/work/code/java/GetFavicon/test_data/test2.svg"));
            if (diagram.getWidth()!=diagram.getHeight())  throw new ExternalException(
                "Image has different sizes: "+diagram.getWidth()+"x"+diagram.getHeight());
            BufferedImage image = Application.drawSvg(diagram, 56);
            ImageIO.write(image, "png", output);

        }
    }
}
