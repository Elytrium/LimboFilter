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

import com.google.common.primitives.Floats;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import it.unimi.dsi.fastutil.Pair;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.LinearGradientPaint;
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
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

  private final CaptchaPainter painter;
  private final List<CraftMapCanvas> backplates = new ArrayList<>();
  private final List<RenderedFont> fonts = new LinkedList<>();
  private final List<byte[]> colors = new LinkedList<>();
  private final LimboFilter plugin;

  private ThreadPoolExecutor executor;
  private boolean shouldStop;
  private CachedCaptcha cachedCaptcha;
  private CachedCaptcha tempCachedCaptcha;
  private ThreadLocal<Iterator<CraftMapCanvas>> backplatesIterator;
  private ThreadLocal<Iterator<RenderedFont>> fontIterator;
  private ThreadLocal<Iterator<byte[]>> colorIterator;

  public CaptchaGenerator(LimboFilter plugin) {
    this.plugin = plugin;
    if (Settings.IMP.MAIN.FRAMED_CAPTCHA.FRAMED_CAPTCHA_ENABLED) {
      this.painter = new CaptchaPainter(
          MapData.MAP_DIM_SIZE * Settings.IMP.MAIN.FRAMED_CAPTCHA.WIDTH,
          MapData.MAP_DIM_SIZE * Settings.IMP.MAIN.FRAMED_CAPTCHA.HEIGHT);
    } else {
      this.painter = new CaptchaPainter(MapData.MAP_DIM_SIZE, MapData.MAP_DIM_SIZE);
    }
  }

  public void initializeGenerator() {
    try {
      for (String backplatePath : Settings.IMP.MAIN.CAPTCHA_GENERATOR.BACKPLATE_PATHS) {
        if (!backplatePath.isEmpty()) {
          CraftMapCanvas craftMapCanvas = this.createCraftMapCanvas();
          craftMapCanvas.drawImage(this.resizeIfNeeded(ImageIO.read(this.plugin.getFile(backplatePath)),
              this.painter.getWidth(), this.painter.getHeight()), this.painter.getWidth(), this.painter.getHeight());
          this.backplates.add(craftMapCanvas);
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    }

    if (Settings.IMP.MAIN.CAPTCHA_GENERATOR.SAVE_NUMBER_SPELLING_OUTPUT) {
      int from = (int) Math.pow(10, Settings.IMP.MAIN.CAPTCHA_GENERATOR.LENGTH - 1);
      int to = from * 10;

      try (OutputStream output = new FileOutputStream("number_spelling.txt")) {
        for (int i = from; i < to; i++) {
          String result = this.spellNumber(i);
          output.write(String.format("%d %s%s", i, result, System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
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

    if (Settings.IMP.MAIN.CAPTCHA_GENERATOR.GRADIENT.GRADIENT_ENABLED) {
      BufferedImage gradientImage = new BufferedImage(this.painter.getWidth(), this.painter.getHeight(), BufferedImage.TYPE_INT_RGB);
      int[] imageData = ((DataBufferInt) gradientImage.getRaster().getDataBuffer()).getData();
      Graphics2D graphics = gradientImage.createGraphics();

      ThreadLocalRandom random = ThreadLocalRandom.current();
      Settings.MAIN.CAPTCHA_GENERATOR.GRADIENT settings = Settings.IMP.MAIN.CAPTCHA_GENERATOR.GRADIENT;

      Color[] colors = Settings.IMP.MAIN.CAPTCHA_GENERATOR.RGB_COLOR_LIST.stream().map(s -> Color.decode("#" + s)).toArray(Color[]::new);
      float[] fractions = Floats.toArray(settings.FRACTIONS);

      if (colors.length != fractions.length) {
        throw new IllegalStateException("The color list and fraction list must contain the same number of elements");
      }

      for (int i = 0; i < settings.GRADIENTS_COUNT; ++i) {
        LinearGradientPaint paint = new LinearGradientPaint(
            (float) settings.START_X + random.nextFloat() * (float) settings.START_X_RANDOMNESS * this.painter.getWidth(),
            (float) settings.START_Y + random.nextFloat() * (float) settings.START_Y_RANDOMNESS * this.painter.getHeight(),
            (float) settings.END_X - random.nextFloat() * (float) settings.END_X_RANDOMNESS * this.painter.getWidth(),
            (float) settings.END_Y - random.nextFloat() * (float) settings.END_Y_RANDOMNESS * this.painter.getHeight(),
            fractions, colors);

        graphics.setPaint(paint);
        graphics.fillRect(0, 0, gradientImage.getWidth(), gradientImage.getHeight());

        this.colors.add(MapPalette.imageToBytes(imageData,
            new byte[this.painter.getWidth() * this.painter.getHeight()],
            ProtocolVersion.MAXIMUM_VERSION));
      }

      graphics.dispose();
    } else {
      Settings.IMP.MAIN.CAPTCHA_GENERATOR.RGB_COLOR_LIST.forEach(e ->
          this.colors.add(new byte[]{MapPalette.tryFastMatchColor(Integer.parseInt(e, 16) | 0xFF000000, ProtocolVersion.MAXIMUM_VERSION)}));
    }

    this.backplatesIterator = ThreadLocal.withInitial(this.backplates::listIterator);
    this.fontIterator = ThreadLocal.withInitial(this.fonts::listIterator);
    this.colorIterator = ThreadLocal.withInitial(this.colors::listIterator);
  }

  private CraftMapCanvas createCraftMapCanvas() {
    if (Settings.IMP.MAIN.FRAMED_CAPTCHA.FRAMED_CAPTCHA_ENABLED) {
      return new CraftMapCanvas(Settings.IMP.MAIN.FRAMED_CAPTCHA.WIDTH, Settings.IMP.MAIN.FRAMED_CAPTCHA.HEIGHT);
    } else {
      return new CraftMapCanvas(1, 1);
    }
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

  private BufferedImage resizeIfNeeded(BufferedImage image, int width, int height) {
    if (image.getWidth() != width || image.getHeight() != height) {
      BufferedImage resizedImage = new BufferedImage(width, height, image.getType());

      Graphics2D graphics = resizedImage.createGraphics();
      graphics.drawImage(image.getScaledInstance(width, height, Image.SCALE_SMOOTH), 0, 0, null);
      graphics.dispose();

      return resizedImage;
    } else {
      return image;
    }
  }

  private void rotate(MapData mapData) {
    byte[] mapImage = mapData.getData();
    byte[] temp = new byte[MapData.MAP_SIZE];
    for (int y = 0; y < MapData.MAP_DIM_SIZE; y++) {
      for (int x = 0; x < MapData.MAP_DIM_SIZE; x++) {
        temp[y * MapData.MAP_DIM_SIZE + x] = mapImage[x * MapData.MAP_DIM_SIZE + MapData.MAP_DIM_SIZE - y - 1];
      }
    }
    System.arraycopy(temp, 0, mapImage, 0, MapData.MAP_SIZE);
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
    Pair<String, String> answer = this.randomAnswer();

    CraftMapCanvas map;
    if (this.backplates.isEmpty()) {
      map = this.createCraftMapCanvas();
    } else {
      if (!this.backplatesIterator.get().hasNext()) {
        this.backplatesIterator.set(this.backplates.listIterator());
      }

      map = new CraftMapCanvas(this.backplatesIterator.get().next());
    }

    if (!this.fontIterator.get().hasNext()) {
      this.fontIterator.set(this.fonts.listIterator());
    }

    map.drawImageCraft(this.painter.drawCaptcha(this.fontIterator.get().next(), this.nextColor(), answer.key()),
        this.painter.getWidth(), this.painter.getHeight());
    map.drawImage(this.painter.drawCurves(), this.painter.getWidth(), this.painter.getHeight());

    Function<MapPalette.MapVersion, MinecraftPacket[]> packet
        = mapVersion -> {
          ThreadLocalRandom random = ThreadLocalRandom.current();
          MinecraftPacket[] packets = new MinecraftPacket[map.getWidth() * map.getHeight()];

          for (int mapId = 0; mapId < packets.length; mapId++) {
            MapData mapData = map.getMapData(mapId, mapVersion);
            if (Settings.IMP.MAIN.FRAMED_CAPTCHA.FRAMED_CAPTCHA_ENABLED
                && random.nextDouble() <= Settings.IMP.MAIN.FRAMED_CAPTCHA.FRAME_ROTATION_CHANCE) {
              for (int j = 0; j < random.nextInt(4); ++j) {
                this.rotate(mapData);
              }
            }
            packets[mapId] = (MinecraftPacket) this.plugin.getPacketFactory().createMapDataPacket(mapId, (byte) 0, mapData);
          }

          return packets;
        };
    MinecraftPacket[] packets17;
    if (this.plugin.getLimboFactory().getPrepareMinVersion().compareTo(ProtocolVersion.MINECRAFT_1_7_6) <= 0) {
      int mapCount = map.getWidth() * map.getHeight();
      packets17 = new MinecraftPacket[MapData.MAP_DIM_SIZE * mapCount];
      for (int mapId = 0; mapId < mapCount; mapId++) {
        MapData[] maps17Data = map.getMaps17Data(mapId);
        for (int i = 0; i < MapData.MAP_DIM_SIZE; ++i) {
          packets17[mapId * MapData.MAP_DIM_SIZE + i] =
              (MinecraftPacket) this.plugin.getPacketFactory().createMapDataPacket(mapId, (byte) 0, maps17Data[i]);
        }
      }
    } else {
      packets17 = new MinecraftPacket[0];
    }

    cachedCaptcha.addCaptchaPacket(answer.value(), packets17, packet);
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

  private String spellNumber(int number) {
    StringBuilder result = new StringBuilder();

    Map<String, String> exceptions = Settings.IMP.MAIN.CAPTCHA_GENERATOR.NUMBER_SPELLING_EXCEPTIONS;
    List<List<String>> words = Settings.IMP.MAIN.CAPTCHA_GENERATOR.NUMBER_SPELLING_WORDS;

    int idx = Settings.IMP.MAIN.CAPTCHA_GENERATOR.LENGTH;
    String n = String.valueOf(number);

    while (!n.isEmpty()) {
      if (exceptions.containsKey(n)) {
        result.append(exceptions.get(n)).append(' ');
        break;
      }

      idx--;

      int digit = n.charAt(0) - '0';
      String word = words.get(idx).get(digit);

      if (word != null && !word.isBlank()) {
        result.append(word).append(' ');
      }

      n = n.substring(1);
    }

    return result.toString();
  }

  private Pair<String, String> randomAnswer() {
    int length = Settings.IMP.MAIN.CAPTCHA_GENERATOR.LENGTH;
    if (!Settings.IMP.MAIN.CAPTCHA_GENERATOR.NUMBER_SPELLING) {
      String pattern = Settings.IMP.MAIN.CAPTCHA_GENERATOR.PATTERN;

      char[] text = new char[length];
      for (int i = 0; i < length; ++i) {
        text[i] = pattern.charAt(ThreadLocalRandom.current().nextInt(pattern.length()));
      }

      String answer = new String(text);
      return Pair.of(answer, answer);
    } else {
      int min = (int) Math.pow(10, length - 1);
      final int value = ThreadLocalRandom.current().nextInt(min, min * 10);
      return Pair.of(this.spellNumber(value), String.valueOf(value));
    }
  }

  private byte[] nextColor() {
    if (!this.colorIterator.get().hasNext()) {
      this.colorIterator.set(this.colors.listIterator());
    }

    return this.colorIterator.get().next();
  }
}
