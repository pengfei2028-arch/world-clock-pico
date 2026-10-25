import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import org.byted.idea.spatial.common.utils.SdfGenerator;

// Uses the installed PICO App Identity Configurator's own implementation and
// its 1024px defaults: inside distance 32, outside 0, postprocess 0,
// Distance RGB mode, alpha normalization 200. No plugin code is redistributed.
public class GeneratePicoSdf {
  public static void main(String[] args) throws Exception {
    BufferedImage image = ImageIO.read(new File(args[0]));
    int w=image.getWidth(), h=image.getHeight();
    byte[] src=new byte[w*h*4], dst=new byte[w*h*4];
    for (int y=0;y<h;y++) for(int x=0;x<w;x++) {
      int c=image.getRGB(x,y), i=(y*w+x)*4;
      src[i]=(byte)(c>>16); src[i+1]=(byte)(c>>8);
      src[i+2]=(byte)c; src[i+3]=(byte)(c>>>24);
    }
    SdfGenerator.INSTANCE.generateRaw(src,dst,w,h,32f,0f,0f,SdfGenerator.RGBFillMode.Distance,200);
    BufferedImage result=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
    for (int y=0;y<h;y++) for(int x=0;x<w;x++) {
      int i=(y*w+x)*4;
      int c=((dst[i+3]&255)<<24)|((dst[i]&255)<<16)|((dst[i+1]&255)<<8)|(dst[i+2]&255);
      result.setRGB(x,y,c);
    }
    ImageIO.write(result,"png",new File(args[1]));
    System.out.println("Official PICO SDF generated: "+args[1]);
  }
}
