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
import java.awt.GraphicsEnvironment;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import net.elytrium.limboapi.api.protocol.packets.BuiltInPackets;
import net.elytrium.limbofilter.LimboFilter;
import net.elytrium.limbofilter.Settings;
import net.elytrium.limbofilter.captcha.map.CraftMapCanvas;
import net.elytrium.limbofilter.captcha.painter.CaptchaPainter;
import org.slf4j.Logger;

public class CaptchaGenerator {

  private static final List<CraftMapCanvas> cachedBackgroundMap = new ArrayList<>();
  private static final CaptchaPainter painter = new CaptchaPainter();
  private static final List<Font> fonts = new ArrayList<>();
  private static final AtomicInteger backplatesCounter = new AtomicInteger(0);
  private static final AtomicInteger fontCounter = new AtomicInteger(0);
  private static final AtomicInteger colorCounter = new AtomicInteger(0);

  private final LimboFilter plugin;
  private final Logger logger;

  public CaptchaGenerator(LimboFilter plugin) {
    this.plugin = plugin;
    this.logger = this.plugin.getLogger();
  }

  public void generateCaptcha() {
    try {
      for (String backplatePath : Settings.IMP.MAIN.CAPTCHA_GENERATOR.BACKPLATE_PATHS) {
        if (!backplatePath.isEmpty()) {
          CraftMapCanvas craftMapCanvas = new CraftMapCanvas();
          craftMapCanvas.drawImage(0, 0, ImageIO.read(new File(backplatePath)), false);
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
          if (!fontFile.equals("")) {
            this.logger.info("Loading font " + fontFile);
            Font font = Font.createFont(Font.TRUETYPE_FONT, new File(fontFile));
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

  @SuppressWarnings("StatementWithEmptyBody")
  public void generateImages() {
    ThreadPoolExecutor ex = (ThreadPoolExecutor) Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    for (int i = 0; i < Settings.IMP.MAIN.CAPTCHA_GENERATOR.IMAGES_COUNT; ++i) {
      ex.execute(this::genNewPacket);
    }

    long start = System.currentTimeMillis();
    while (ex.getActiveCount() != 0) {
      // Busy wait
    }

    this.logger.info("Captcha generated in " + (System.currentTimeMillis() - start) + " ms.");
    ex.shutdownNow();
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

    BufferedImage image = painter.draw(fonts.get(fontNumber), this.randomColor(), answer);
    map.drawImage(0, 0, image, Settings.IMP.MAIN.CAPTCHA_GENERATOR.COLORIFY);

    MinecraftPacket packet = (MinecraftPacket) this.plugin.getFactory().instantiatePacket(BuiltInPackets.MapData, 0, (byte) 0, map.getMapData());
    MinecraftPacket[] packets17 = new MinecraftPacket[128];
    for (int i = 0; i < 128; ++i) {
      packets17[i] = (MinecraftPacket) this.plugin.getFactory().instantiatePacket(BuiltInPackets.MapData, 0, (byte) 0, map.get17MapsData()[i]);
    }

    this.plugin.getCachedCaptcha().createCaptchaPacket(packet, packets17, answer);
  }

  private Color randomColor() {
    int index = colorCounter.getAndIncrement();
    if (index >= Settings.IMP.MAIN.CAPTCHA_GENERATOR.RGB_COLOR_LIST.size()) {
      index = 0;
      colorCounter.set(0);
    }

    return simplifyRGB(Integer.parseInt(Settings.IMP.MAIN.CAPTCHA_GENERATOR.RGB_COLOR_LIST.get(index), 16));
  }

  private static Color simplifyRGB(int rgb) {
    int r = ((rgb & 0xff0000) >>> 16) & ~15;
    int g = ((rgb & 0xff00) >>> 8) & ~15;
    int b = (rgb & 0xff) & ~15;

    return new Color(r, g, b);
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
}
