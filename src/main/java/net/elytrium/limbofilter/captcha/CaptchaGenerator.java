/*
 * Copyright (C) 2021 - 2022 Elytrium
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.elytrium.limbofilter.captcha;

import com.velocitypowered.proxy.protocol.MinecraftPacket;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import net.elytrium.limboapi.api.protocol.packets.data.MapData;
import net.elytrium.limbofilter.LimboFilter;
import net.elytrium.limbofilter.Settings;
import net.elytrium.limbofilter.captcha.map.CraftMapCanvas;
import net.elytrium.limbofilter.captcha.painter.CaptchaPainter;

public class CaptchaGenerator {

  private static final List<CraftMapCanvas> cachedBackgroundMap = new ArrayList<>();
  private static final CaptchaPainter painter = new CaptchaPainter();
  private static final List<Font> fonts = new ArrayList<>();
  private static final AtomicInteger backplatesCounter = new AtomicInteger();
  private static final AtomicInteger fontCounter = new AtomicInteger();
  private static final AtomicInteger colorCounter = new AtomicInteger();

  private final LimboFilter plugin;

  public CaptchaGenerator(LimboFilter plugin) {
    this.plugin = plugin;
  }

  public void generateCaptcha() {
    try {
      for (String backplatePath : Settings.IMP.MAIN.CAPTCHA_GENERATOR.BACKPLATE_PATHS) {
        if (!backplatePath.isEmpty()) {
          CraftMapCanvas craftMapCanvas = new CraftMapCanvas();
          craftMapCanvas.drawImage(0, 0, this.resizeIfNeeded(ImageIO.read(this.plugin.getFile(backplatePath))));
          cachedBackgroundMap.add(craftMapCanvas);
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    }

    fonts.clear();

    int fontSize = Settings.IMP.MAIN.CAPTCHA_GENERATOR.FONT_SIZE;
    Map<TextAttribute, Object> textSettings = Map.of(
        TextAttribute.TRACKING,
        Settings.IMP.MAIN.CAPTCHA_GENERATOR.LETTER_SPACING,
        TextAttribute.SIZE,
        (float) fontSize,
        TextAttribute.STRIKETHROUGH,
        Settings.IMP.MAIN.CAPTCHA_GENERATOR.STRIKETHROUGH,
        TextAttribute.UNDERLINE,
        Settings.IMP.MAIN.CAPTCHA_GENERATOR.UNDERLINE
    );

    if (Settings.IMP.MAIN.CAPTCHA_GENERATOR.USE_STANDARD_FONTS) {
      fonts.add(new Font(Font.SANS_SERIF, Font.PLAIN, fontSize).deriveFont(textSettings));
      fonts.add(new Font(Font.SERIF, Font.PLAIN, fontSize).deriveFont(textSettings));
      fonts.add(new Font(Font.MONOSPACED, Font.PLAIN, fontSize).deriveFont(textSettings));
    }

    if (Settings.IMP.MAIN.CAPTCHA_GENERATOR.FONTS_PATH != null) {
      Settings.IMP.MAIN.CAPTCHA_GENERATOR.FONTS_PATH.forEach(fontFile -> {
        try {
          if (!fontFile.isEmpty()) {
            LimboFilter.getLogger().info("Loading font " + fontFile + ".");
            Font font = Font.createFont(Font.TRUETYPE_FONT, this.plugin.getFile(fontFile));
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
            fonts.add(font.deriveFont(textSettings));
          }
        } catch (FontFormatException | IOException e) {
          e.printStackTrace();
        }
      });
    }

    new Thread(this::generateImages).start();
  }

  private BufferedImage resizeIfNeeded(BufferedImage image) {
    if (image.getWidth() != MapData.MAP_DIM_SIZE || image.getHeight() != MapData.MAP_DIM_SIZE) {
      BufferedImage resizedImage = new BufferedImage(MapData.MAP_DIM_SIZE, MapData.MAP_DIM_SIZE, image.getType());

      Graphics2D graphics = resizedImage.createGraphics();
      graphics.drawImage(image.getScaledInstance(MapData.MAP_DIM_SIZE, MapData.MAP_DIM_SIZE, Image.SCALE_SMOOTH), 0, 0, null);
      graphics.dispose();

      return resizedImage;
    } else {
      return image;
    }
  }

  @SuppressWarnings("StatementWithEmptyBody")
  public void generateImages() {
    ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    for (int i = 0; i < Settings.IMP.MAIN.CAPTCHA_GENERATOR.IMAGES_COUNT; ++i) {
      executor.execute(this::genNewPacket);
    }

    long start = System.currentTimeMillis();
    while (executor.getCompletedTaskCount() != Settings.IMP.MAIN.CAPTCHA_GENERATOR.IMAGES_COUNT) {
      // Busy wait.
    }

    LimboFilter.getLogger().info("Captcha generated in " + (System.currentTimeMillis() - start) + " ms.");
    executor.shutdownNow();
    System.gc();
  }

  public void genNewPacket() {
    String answer = this.randomAnswer();

    CraftMapCanvas map;
    if (cachedBackgroundMap.isEmpty()) {
      map = new CraftMapCanvas();
    } else {
      int backplateNumber = backplatesCounter.getAndIncrement();
      if (backplateNumber >= cachedBackgroundMap.size()) {
        backplateNumber = 0;
        backplatesCounter.set(0);
      }

      map = new CraftMapCanvas(cachedBackgroundMap.get(backplateNumber));
    }

    int fontNumber = fontCounter.getAndIncrement();
    if (fontNumber >= fonts.size()) {
      fontNumber = 0;
      fontCounter.set(0);
    }

    map.drawImage(0, 0, painter.drawCaptcha(fonts.get(fontNumber), this.randomColor(), answer));

    MinecraftPacket packet = (MinecraftPacket) this.plugin.getPacketFactory().createMapDataPacket(0, (byte) 0, map.getMapData());
    MinecraftPacket[] packets17 = new MinecraftPacket[MapData.MAP_DIM_SIZE];
    for (int i = 0; i < MapData.MAP_DIM_SIZE; ++i) {
      packets17[i] = (MinecraftPacket) this.plugin.getPacketFactory().createMapDataPacket(0, (byte) 0, map.getMaps17Data()[i]);
    }

    this.plugin.getCachedCaptcha().createCaptchaPacket(packet, packets17, answer);
  }

  private String randomAnswer() {
    int length = Settings.IMP.MAIN.CAPTCHA_GENERATOR.LENGTH;
    String pattern = Settings.IMP.MAIN.CAPTCHA_GENERATOR.PATTERN;

    char[] text = new char[length];
    for (int i = 0; i < length; ++i) {
      text[i] = pattern.charAt(ThreadLocalRandom.current().nextInt(pattern.length()));
    }

    return new String(text);
  }

  private Color randomColor() {
    int index = colorCounter.getAndIncrement();
    if (index >= Settings.IMP.MAIN.CAPTCHA_GENERATOR.RGB_COLOR_LIST.size()) {
      colorCounter.set(0);
      index = 0;
    }

    return this.downscaleRGB(Integer.parseInt(Settings.IMP.MAIN.CAPTCHA_GENERATOR.RGB_COLOR_LIST.get(index), 16));
  }

  private Color downscaleRGB(int rgb) {
    int r = ((rgb & 0xFF0000) >>> 16) & ~15;
    int g = ((rgb & 0xFF00) >>> 8) & ~15;
    int b = (rgb & 0xFF) & ~15;

    return new Color(r, g, b);
  }
}
