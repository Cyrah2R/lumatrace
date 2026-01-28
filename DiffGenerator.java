import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class DiffGenerator {
    public static void main(String[] args) throws Exception {
        BufferedImage var1 = ImageIO.read(new File("samples/input.jpg"));
        BufferedImage var2 = ImageIO.read(new File("samples/protected_final.jpg"));

        int var3 = var1.getWidth();
        int var4 = var1.getHeight();
        BufferedImage var5 = new BufferedImage(var3, var4, BufferedImage.TYPE_INT_RGB);

        for(int var6 = 0; var6 < var4; ++var6) {
            for(int var7 = 0; var7 < var3; ++var7) {
                Color var8 = new Color(var1.getRGB(var7, var6));
                Color var9 = new Color(var2.getRGB(var7, var6));

                int var10 = Math.min(255, Math.abs(var8.getRed() - var9.getRed()) * 10);
                int var11 = Math.min(255, Math.abs(var8.getGreen() - var9.getGreen()) * 10);
                int var12 = Math.min(255, Math.abs(var8.getBlue() - var9.getBlue()) * 10);

                var5.setRGB(var7, var6, (new Color(var10, var11, var12)).getRGB());
            }
        }

        new File("docs/screenshots").mkdirs();

        ImageIO.write(var5, "png", new File("docs/screenshots/difference_map.png"));
        System.out.println("Mapa de diferencia generado en docs/screenshots/difference_map.png");
    }
}