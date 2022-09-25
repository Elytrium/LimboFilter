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

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Function;
import javax.imageio.ImageIO;
import net.elytrium.limboapi.api.protocol.packets.data.MapData;
import net.elytrium.limboapi.api.protocol.packets.data.MapPalette;
import net.elytrium.limbofilter.LimboFilter;
import net.elytrium.limbofilter.Settings;
import net.elytrium.limbofilter.cache.captcha.CachedCaptcha;
import net.elytrium.limbofilter.captcha.map.CraftMapCanvas;
import net.elytrium.limbofilter.captcha.painter.CaptchaPainter;
import net.elytrium.limbofilter.captcha.painter.RenderedFont;

public class CaptchaGenerator {

  private final CaptchaPainter painter = new CaptchaPainter();
  private final List<CraftMapCanvas> backplates = new ArrayList<>();
  private final List<RenderedFont> fonts = new LinkedList<>();
  private final List<Byte> colors = new LinkedList<>();
  private final LimboFilter plugin;

  private ThreadPoolExecutor executor;
  private boolean shouldStop;
  private CachedCaptcha cachedCaptcha;
  private CachedCaptcha tempCachedCaptcha;
  private ThreadLocal<Iterator<CraftMapCanvas>> backplatesIterator;
  private ThreadLocal<Iterator<RenderedFont>> fontIterator;
  private ThreadLocal<Iterator<Byte>> colorIterator;

  public CaptchaGenerator(LimboFilter plugin) {
    this.plugin = plugin;
  }

  public void initializeGenerator() {
    try {
      for (String backplatePath : Settings.IMP.MAIN.CAPTCHA_GENERATOR.BACKPLATE_PATHS) {
        if (!backplatePath.isEmpty()) {
          CraftMapCanvas craftMapCanvas = new CraftMapCanvas();
          craftMapCanvas.drawImage(this.resizeIfNeeded(ImageIO.read(this.plugin.getFile(backplatePath))));
          this.backplates.add(craftMapCanvas);
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    }

    this.fonts.clear();

    float fontSize = (float) Settings.IMP.MAIN.CAPTCHA_GENERATOR.RENDER_FONT_SIZE;
    Map<TextAttribute, Object> textSettings = Map.of(
        TextAttribute.SIZE,
        fontSize,
        TextAttribute.STRIKETHROUGH,
        Settings.IMP.MAIN.CAPTCHA_GENERATOR.STRIKETHROUGH,
        TextAttribute.UNDERLINE,
        Settings.IMP.MAIN.CAPTCHA_GENERATOR.UNDERLINE
    );

    if (Settings.IMP.MAIN.CAPTCHA_GENERATOR.USE_STANDARD_FONTS) {
      this.fonts.add(this.getRenderedFont(new Font(Font.SANS_SERIF, Font.PLAIN, (int) fontSize).deriveFont(textSettings)));
      this.fonts.add(this.getRenderedFont(new Font(Font.SERIF, Font.PLAIN, (int) fontSize).deriveFont(textSettings)));
      this.fonts.add(this.getRenderedFont(new Font(Font.MONOSPACED, Font.PLAIN, (int) fontSize).deriveFont(textSettings)));
    }

    if (Settings.IMP.MAIN.CAPTCHA_GENERATOR.FONTS_PATH != null) {
      Settings.IMP.MAIN.CAPTCHA_GENERATOR.FONTS_PATH.forEach(fontFile -> {
        try {
          if (!fontFile.isEmpty()) {
            LimboFilter.getLogger().info("Loading font " + fontFile + ".");
            Font font = Font.createFont(Font.TRUETYPE_FONT, this.plugin.getFile(fontFile));
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
            this.fonts.add(this.getRenderedFont(font.deriveFont(textSettings)));
          }
        } catch (FontFormatException | IOException e) {
          e.printStackTrace();
        }
      });
    }

    Settings.IMP.MAIN.CAPTCHA_GENERATOR.RGB_COLOR_LIST.forEach(e ->
        this.colors.add(MapPalette.tryFastMatchColor(Integer.parseInt(e, 16) | 0xFF000000, ProtocolVersion.MAXIMUM_VERSION)));

    this.backplatesIterator = ThreadLocal.withInitial(this.backplates::listIterator);
    this.fontIterator = ThreadLocal.withInitial(this.fonts::listIterator);
    this.colorIterator = ThreadLocal.withInitial(this.colors::listIterator);
  }

  private RenderedFont getRenderedFont(Font font) {
    return new RenderedFont(font,
        new FontRenderContext(null, true, true),
        Settings.IMP.MAIN.CAPTCHA_GENERATOR.PATTERN.toCharArray(),
        Settings.IMP.MAIN.CAPTCHA_GENERATOR.FONT_LETTER_WIDTH,
        Settings.IMP.MAIN.CAPTCHA_GENERATOR.FONT_LETTER_HEIGHT,
        Settings.IMP.MAIN.CAPTCHA_GENERATOR.FONT_OUTLINE,
        (float) Settings.IMP.MAIN.CAPTCHA_GENERATOR.FONT_OUTLINE_RATE,
        Settings.IMP.MAIN.CAPTCHA_GENERATOR.FONT_OUTLINE_OFFSET_X,
        Settings.IMP.MAIN.CAPTCHA_GENERATOR.FONT_OUTLINE_OFFSET_Y,
        1.35
    );
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
    if (this.shouldStop) {
      return;
    }
    this.shouldStop = true;

    if (this.tempCachedCaptcha != null) {
      this.tempCachedCaptcha.dispose();
    }

    int threadsCount = Runtime.getRuntime().availableProcessors();
    this.tempCachedCaptcha = new CachedCaptcha(this.plugin, threadsCount);
    this.executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(threadsCount);
    ThreadGroup threadGroup = Thread.currentThread().getThreadGroup();
    LinkedList<Thread> threads = new LinkedList<>();
    this.executor.setThreadFactory(runnable -> {
      Thread thread = new Thread(threadGroup, runnable, "CaptchaGeneratorThread");
      threads.add(thread);
      thread.setPriority(Thread.MIN_PRIORITY);
      return thread;
    });

    for (int i = 0; i < Settings.IMP.MAIN.CAPTCHA_GENERATOR.IMAGES_COUNT; ++i) {
      this.executor.execute(() -> this.genNewPacket(this.tempCachedCaptcha));
    }

    long start = System.currentTimeMillis();
    this.executor.execute(() -> {
      while (this.executor.getCompletedTaskCount() != Settings.IMP.MAIN.CAPTCHA_GENERATOR.IMAGES_COUNT) {
        // Busy wait.
      }

      LimboFilter.getLogger().info("Captcha generated in " + (System.currentTimeMillis() - start) + " ms.");

      if (this.cachedCaptcha != null) {
        this.cachedCaptcha.dispose();
      }

      threads.forEach(this.plugin.getLimboFactory()::releasePreparedPacketThread);
      threads.clear();

      this.cachedCaptcha = this.tempCachedCaptcha;
      this.tempCachedCaptcha = null;
      this.cachedCaptcha.build();
      this.executor.shutdown();
      this.shouldStop = false;
    });
  }

  public void genNewPacket(CachedCaptcha cachedCaptcha) {
    String answer = this.randomAnswer();

    CraftMapCanvas map;
    if (this.backplates.isEmpty()) {
      map = new CraftMapCanvas();
    } else {
      if (!this.backplatesIterator.get().hasNext()) {
        this.backplatesIterator.set(this.backplates.listIterator());
      }

      map = new CraftMapCanvas(this.backplatesIterator.get().next());
    }

    if (!this.fontIterator.get().hasNext()) {
      this.fontIterator.set(this.fonts.listIterator());
    }
    map.drawImage(this.painter.drawOvals());
    map.drawImageCraft(this.painter.drawCaptcha(this.fontIterator.get().next(), this.nextColor(), answer));
    map.drawImage(this.painter.drawCurves());

    Function<MapPalette.MapVersion, MinecraftPacket> packet
        = mapVersion -> (MinecraftPacket) this.plugin.getPacketFactory().createMapDataPacket(0, (byte) 0, map.getMapData(mapVersion));
    MinecraftPacket[] packets17;
    if (this.plugin.getLimboFactory().getPrepareMinVersion().compareTo(ProtocolVersion.MINECRAFT_1_7_6) <= 0) {
      packets17 = new MinecraftPacket[MapData.MAP_DIM_SIZE];
      MapData[] maps17Data = map.getMaps17Data();
      for (int i = 0; i < MapData.MAP_DIM_SIZE; ++i) {
        packets17[i] = (MinecraftPacket) this.plugin.getPacketFactory().createMapDataPacket(0, (byte) 0, maps17Data[i]);
      }
    } else {
      packets17 = new MinecraftPacket[0];
    }

    cachedCaptcha.addCaptchaPacket(answer, packets17, packet);
  }

  public void shutdown() {
    this.shouldStop = true;
    if (this.executor != null) {
      this.executor.shutdownNow();
    }

    if (this.tempCachedCaptcha != null) {
      this.tempCachedCaptcha.dispose();
    }

    if (this.cachedCaptcha != null) {
      this.cachedCaptcha.dispose();
    }
  }

  public CaptchaHolder getNextCaptcha() {
    if (this.cachedCaptcha == null) {
      return null;
    } else {
      return this.cachedCaptcha.getNextCaptcha();
    }
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

  private Byte nextColor() {
    if (!this.colorIterator.get().hasNext()) {
      this.colorIterator.set(this.colors.listIterator());
    }

    return this.colorIterator.get().next();
  }
}
