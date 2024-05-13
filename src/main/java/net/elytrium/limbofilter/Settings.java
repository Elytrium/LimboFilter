/*
 * Copyright (C) 2021 - 2023 Elytrium
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

package net.elytrium.limbofilter;

import java.util.List;
import java.util.Map;
import net.elytrium.commons.config.YamlConfig;
import net.elytrium.commons.kyori.serialization.Serializers;
import net.elytrium.limboapi.api.chunk.Dimension;
import net.elytrium.limboapi.api.file.BuiltInWorldFileType;
import net.elytrium.limboapi.api.player.GameMode;
import net.elytrium.limbofilter.commands.CommandPermissionState;
import net.elytrium.limbofilter.handler.BotFilterSessionHandler;

public class Settings extends YamlConfig {

  @Ignore
  public static final Settings IMP = new Settings();

  @Final
  public String VERSION = BuildConstants.FILTER_VERSION;

  @Comment({
      "Available serializers:",
      "LEGACY_AMPERSAND - \"&c&lExample &c&9Text\".",
      "LEGACY_SECTION - \"§c§lExample §c§9Text\".",
      "MINIMESSAGE - \"<bold><red>Example</red> <blue>Text</blue></bold>\". (https://webui.adventure.kyori.net/)",
      "GSON - \"[{\"text\":\"Example\",\"bold\":true,\"color\":\"red\"},{\"text\":\" \",\"bold\":true},{\"text\":\"Text\",\"bold\":true,\"color\":\"blue\"}]\". (https://minecraft.tools/en/json_text.php/)",
      "GSON_COLOR_DOWNSAMPLING - Same as GSON, but uses downsampling."
  })
  public Serializers SERIALIZER = Serializers.LEGACY_AMPERSAND;
  public String PREFIX = "LimboFilter &6>>&f";

  @Create
  public MAIN MAIN;

  @Comment("Don't use \\n, use {NL} for new line, and {PRFX} for prefix.")
  public static class MAIN {

    @Comment("Check if player's Minecraft client sends the network packet with the settings.")
    public boolean CHECK_CLIENT_SETTINGS = true;
    @Comment("Check if player's Minecraft client has a brand.")
    public boolean CHECK_CLIENT_BRAND = true;
    @Comment("If a player's Minecraft client brand (e.g., fabric or forge) is set here, then that player will be kicked.")
    public List<String> BLOCKED_CLIENT_BRANDS = List.of("brand1", "brand2");
    @Comment("Time in milliseconds, how frequently will the cache list with verified players be reset. Before that time, verified players can join the server without passing antibot checks.")
    public long PURGE_CACHE_MILLIS = 3600000;
    @Comment("Max attempts, which a player has to solve the captcha.")
    public int CAPTCHA_ATTEMPTS = 2;
    @Comment("Duration of Falling Check in Minecraft ticks (1 tick = 0.05 second, 20 ticks = 1 second).")
    public int FALLING_CHECK_TICKS = 128;
    @Comment("Maximum time to check the player in milliseconds. If the player stays on the filter limbo for longer than this time, then the check will fail.")
    public int TIME_OUT = 15000;
    @Comment("Same, but for Geyser users.")
    public int GEYSER_TIME_OUT = 45000;
    @Comment("The timeout for Netty. Max ping while being on the filter limbo. Used to remove useless buffers from RAM.")
    public int MAX_PING = 3500;
    @Comment("Change the parameters below only if you know what they mean.")
    public int NON_VALID_POSITION_XZ_ATTEMPTS = 10;
    public int NON_VALID_POSITION_Y_ATTEMPTS = 10;
    public double MAX_VALID_POSITION_DIFFERENCE = 0.01;
    @Comment("Parameter for developers and contributors.")
    public boolean FALLING_CHECK_DEBUG = false;
    @Comment("Should captcha be displayed in the left hand. May cause problems with entering captcha for users with 4:3 monitors. Version: 1.9+")
    public boolean CAPTCHA_LEFT_HAND = false;

    @Comment({
        "Available states: ONLY_POSITION, ONLY_CAPTCHA, CAPTCHA_POSITION, CAPTCHA_ON_POSITION_FAILED",
        "Meaning: ",
        "ONLY_POSITION -> Only falling check (Player will be spawned in the void, server will check player's coordinates, speed, acceleration).",
        "ONLY_CAPTCHA -> Only captcha (Map items with a captcha image will be given to the players, players need to solve captcha, and send the answer in the chat).",
        "CAPTCHA_POSITION -> Falling and Captcha checking concurrently (Player will be kicked, if he fails either falling check or captcha checking).",
        "CAPTCHA_ON_POSITION_FAILED -> Initially, the falling check will be started, but if the player fails that check, the captcha checking will be started."
    })
    public BotFilterSessionHandler.CheckState CHECK_STATE = BotFilterSessionHandler.CheckState.CAPTCHA_POSITION;
    @Comment("See \"filter-auto-toggle.check-state-toggle\".")
    public BotFilterSessionHandler.CheckState CHECK_STATE_NON_TOGGLED = BotFilterSessionHandler.CheckState.CAPTCHA_ON_POSITION_FAILED;

    @Comment("See \"filter-auto-toggle.check-state-toggle\".")
    public BotFilterSessionHandler.CheckState GEYSER_CHECK_STATE = BotFilterSessionHandler.CheckState.CAPTCHA_POSITION;
    @Comment("See \"filter-auto-toggle.check-state-toggle\".")
    public BotFilterSessionHandler.CheckState GEYSER_CHECK_STATE_NON_TOGGLED = BotFilterSessionHandler.CheckState.CAPTCHA_ON_POSITION_FAILED;

    public boolean LOAD_WORLD = false;
    @Comment({
        "World file types:",
        " SCHEMATIC (MCEdit .schematic, 1.12.2 and lower, not recommended)",
        " STRUCTURE (structure block .nbt, any Minecraft version is supported, but the latest one is recommended).",
        " WORLDEDIT_SCHEM (WorldEdit .schem, any Minecraft version is supported, but the latest one is recommended)."
    })
    public BuiltInWorldFileType WORLD_FILE_TYPE = BuiltInWorldFileType.STRUCTURE;
    public String WORLD_FILE_PATH = "world.nbt";

    @Comment("World time in ticks (24000 ticks == 1 in-game day)")
    public long WORLD_TICKS = 1000L;

    @Comment("World light level (from 0 to 15)")
    public int WORLD_LIGHT_LEVEL = 15;

    @Comment("Should we override block light level (to light up the nether and the end)")
    public boolean WORLD_OVERRIDE_BLOCK_LIGHT_LEVEL = true;

    @Comment("Available: ADVENTURE, CREATIVE, SURVIVAL, SPECTATOR")
    public GameMode GAME_MODE = GameMode.ADVENTURE;

    @Comment("Unit of time in seconds for the Auto Toggles and Statistics.")
    public int UNIT_OF_TIME_CPS = 300;

    @Comment("Unit of time in seconds for the Auto Toggles and the Statistics.")
    public int UNIT_OF_TIME_PPS = 5;

    @Comment("Time in milliseconds how much we should wait before re-enabling logs after attacks")
    public int LOG_ENABLER_CHECK_REFRESH_RATE = 1000;

    @Comment("Duration (in seconds) between regeneration of captchas")
    public long CAPTCHA_REGENERATE_RATE = 3600;

    @Comment("Coordinates for the falling check")
    @Create
    public Settings.MAIN.FALLING_COORDS FALLING_COORDS;

    public static class FALLING_COORDS {
      public int X = 0;
      public int Y = 512;
      public int Z = 0;
      public int TELEPORT_ID = 44;
    }

    @Comment("A \"USERNAME - IP\" list containing information about players who should join the server without verification.")
    public List<WhitelistedPlayer> WHITELISTED_PLAYERS = List.of(new WhitelistedPlayer());

    public static class WhitelistedPlayer {

      public String USERNAME = "TestUser123";
      public String IP = "127.0.0.1";
    }

    @Create
    public FILTER_AUTO_TOGGLE FILTER_AUTO_TOGGLE;

    @Comment({
        "Minimum/maximum total connections amount per the unit of time to toggle anti-bot checks.",
        "-1 to disable the check.",
        "0 to enable on any connections per the unit of time."
    })
    public static class FILTER_AUTO_TOGGLE {

      // TODO: Норм комменты
      @Comment("All players will bypass all anti-bot checks")
      public int ALL_BYPASS = 0;

      @Comment({
          "Online mode players will bypass all anti-bot checks.",
          "Doesn't work with online-mode-verify: -1"
      })
      public int ONLINE_MODE_BYPASS = 49;

      @Comment({
          "Verify Online Mode connection before AntiBot.",
          "If connections per unit of time amount is bigger than the limit: online mode players will need to reconnect.",
          "Else: Some attacks can consume more cpu and network, and can lead to long-lasting Mojang rate-limiting.",
          "Only works if you have an auth plugin installed. In other cases you should configure need-to-reconnect parameter"
      })
      public int ONLINE_MODE_VERIFY = 79;

      @Comment({
          "Toggles check-state/check-state-non-toggled.",
          "It is not recommended to enable it, if you want to protect your server from spam-bots.",
          "If connections per unit of time amount is bigger than the limit: check-state will be used.",
          "Else: check-state-non-toggled will be used."
      })
      public int CHECK_STATE_TOGGLE = 0;

      @Comment("The player will need to reconnect after passing the AntiBot check.")
      public int NEED_TO_RECONNECT = 129;

      @Comment("Picture in the MOTD Server Ping packet will be disabled.")
      public int DISABLE_MOTD_PICTURE = 25;

      @Comment("All the log messages from all plugins will be disabled.")
      public int DISABLE_LOG = 129;
    }

    @Create
    public Settings.MAIN.WORLD_COORDS WORLD_COORDS;

    public static class WORLD_COORDS {

      public int X = 0;
      public int Y = 0;
      public int Z = 0;
    }

    @Create
    public MAIN.CAPTCHA_GENERATOR CAPTCHA_GENERATOR;

    public static class CAPTCHA_GENERATOR {

      @Comment("Prepares Captcha packets, consumes x8 more RAM, but improves CPU performance during bot attacks. It's recommended to disable it, if you have less than 2GB of RAM.")
      public boolean PREPARE_CAPTCHA_PACKETS = false;
      @Comment("List of paths to the background image to draw on captcha. Any format, 128x128 128x128 px (will be automatically resized and stretched to the correct size). [] if empty.")
      public List<String> BACKPLATE_PATHS = List.of("");
      @Comment("Path to the font files to draw on captcha (ttf), can be empty.")
      public List<String> FONTS_PATH = List.of("");
      @Comment("Use standard fonts(SANS_SERIF/SERIF/MONOSPACED), use false only if you provide fonts path")
      public boolean USE_STANDARD_FONTS = true;
      public int LETTER_OFFSET_X = 12;
      public int LETTER_OFFSET_Y = 0;
      public int FONT_LETTER_SPACING_X = -14;
      public int FONT_LETTER_SPACING_Y = 0;
      public double RENDER_FONT_SIZE = 78.0;
      public int FONT_LETTER_WIDTH = 44;
      public int FONT_LETTER_HEIGHT = 128;
      public boolean FONT_OUTLINE = true;
      public boolean FONT_ROTATE = true;
      public boolean FONT_RIPPLE = true;
      public double FONT_RIPPLE_AMPLITUDE_WIDTH = 100.0;
      public double FONT_RIPPLE_AMPLITUDE_HEIGHT = 10.0;
      public double FONT_OUTLINE_RATE = 1.25;
      public int FONT_OUTLINE_OFFSET_X = -4;
      public int FONT_OUTLINE_OFFSET_Y = 4;
      public int FONT_OUTLINE_OVERRIDE_RADIUS = 1;
      @Comment("Set 0 to disable")
      public int CURVE_SIZE = 2;
      @Comment("Set 0 to disable")
      public int CURVES_AMOUNT = 3;
      @Comment("RGB colors without #")
      public List<String> CURVES_COLORS = List.of("000000");
      public boolean STRIKETHROUGH = false;
      public boolean UNDERLINE = true;
      public String PATTERN = "abcdefghijklmnopqrstuvwxyz1234567890";
      @Comment("If enabled, both lowercase and uppercase captcha answers entered by players will be correct")
      public boolean IGNORE_CASE = true;
      public int LENGTH = 3;
      public int IMAGES_COUNT = 1000;
      public boolean NUMBER_SPELLING = false;
      @Comment({
          "Set to true if you want to verify the number spelling configuration.",
          "Results will be saved to the number_spelling.txt file."
      })
      public boolean SAVE_NUMBER_SPELLING_OUTPUT = false;
      public boolean EACH_WORD_ON_SEPARATE_LINE = true;
      @Comment({
          "If the number ends with any key specified here, the corresponding value will be used.",
          "For example: if exception 11 is specified with value 'eleven', the number 411 will be spelt as 'four hundred eleven'."
      })
      public Map<String, String> NUMBER_SPELLING_EXCEPTIONS = Map.of(
          "11", "eleven",
          "12", "twelve",
          "13", "thirteen",
          "14", "fourteen",
          "15", "fifteen",
          "16", "sixteen",
          "17", "seventeen",
          "18", "eighteen",
          "19", "nineteen"
      );
      @Comment({
          "null or \"\" means that the digit should be skipped.",
          "Note: all the characters used here (including the space) must be listed in pattern."
      })
      public List<List<String>> NUMBER_SPELLING_WORDS = List.of(
          List.of("", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine"),
          List.of("", "ten", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"),
          List.of("", "one hundred", "two hundred", "three hundred", "four hundred", "five hundred", "six hundred", "seven hundred", "eight hundred", "nine hundred")
      );
      public List<String> RGB_COLOR_LIST = List.of("000000", "AA0000", "00AA00", "0000AA", "AAAA00", "AA00AA", "00AAAA");

      @Create
      public GRADIENT GRADIENT;

      public static class GRADIENT {

        public boolean GRADIENT_ENABLED = false;
        public int GRADIENTS_COUNT = 32;
        public double START_X = 0;
        public double START_Y = 40;
        public double END_X = 128;
        public double END_Y = 80;
        public double START_X_RANDOMNESS = 0;
        public double START_Y_RANDOMNESS = 2;
        public double END_X_RANDOMNESS = 0;
        public double END_Y_RANDOMNESS = 2;
        @Comment("Numbers ranging from 0.0 to 1.0 specifying the distribution of colors along the gradient. Can be empty.")
        public List<Double> FRACTIONS = List.of(0.0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9);
      }
    }

    @Create
    public FRAMED_CAPTCHA FRAMED_CAPTCHA;

    public static class FRAMED_CAPTCHA {

      public boolean FRAMED_CAPTCHA_ENABLED = false;
      public int WIDTH = 3;
      public int HEIGHT = 3;
      public double FRAME_ROTATION_CHANCE = 0.33;
      public boolean AUTOSCALE_FONT = true;

      @Create
      public COORDS COORDS;

      public static class COORDS {

        public int X = -3;
        public int Y = 128;
        public int Z = 2;

        @Create
        public OFFSET_1_7 OFFSET_1_7;

        public static class OFFSET_1_7 {

          public int X = 0;
          public int Y = -2;
          public int Z = 1;
        }
      }
    }

    @Comment(
        "Available dimensions: OVERWORLD, NETHER, THE_END"
    )
    public Dimension BOTFILTER_DIMENSION = Dimension.THE_END;

    @Create
    public COORDS COORDS;

    public static class COORDS {

      public double CAPTCHA_X = -1.5;
      @Comment("If your server supports Minecraft 1.7, don't set captcha-y to 0. https://media.discordapp.net/attachments/878241549857738793/915165038464098314/unknown.png")
      public double CAPTCHA_Y = 128;
      public double CAPTCHA_Z = 0.5;
      public double CAPTCHA_YAW = 90;
      public double CAPTCHA_PITCH = 38;
      public double FALLING_CHECK_YAW = 90;
      public double FALLING_CHECK_PITCH = 10;
    }

    @Create
    public MAIN.TCP_LISTENER TCP_LISTENER;

    public static class TCP_LISTENER {

      @Comment({
          "Experimental proxy check feature",
          "Checks the proxy via comparing L4 (TCP PSH+ACK -> TCP ACK) and L7 (Minecraft KeepAlive) ping",
          "Works better with falling check enabled (150+ falling-check-ticks)",
          "Needs libpcap (libpcap-dev) on Linux; WinPcap/npcap on Windows",
          "Needs CAP_NET_RAW (or super-user) on Linux",
          "Doesn't work if Velocity is behind reverse-proxy (haproxy, protection services, etc)",
      })
      public boolean PROXY_DETECTOR_ENABLED = false;

      @Comment("Difference between TCP (L4) and Minecraft (L7) ping in milliseconds to detect proxies.")
      public int PROXY_DETECTOR_DIFFERENCE = 5;

      public String INTERFACE_NAME = "any";

      @Comment("How many bytes we should take from the each frame to analyse. 120 is enough for any TCP+IP header analysing")
      public int SNAPLEN = 120;

      @Comment("How many milliseconds should the delay be between frame analysis.")
      public int LISTEN_DELAY = 50;

      @Comment("Time in millis for capturing frames")
      public int TIMEOUT = 10;

      @Comment("Log L4 and L7 ping")
      public boolean DEBUG_ON_FAIL = false;
      public boolean DEBUG_ON_SUCCESS = false;
    }

    @Create
    public MAIN.COMMAND_PERMISSION_STATE COMMAND_PERMISSION_STATE;

    @Comment({
        "Available values: FALSE, TRUE, PERMISSION",
        " FALSE - the command will be disallowed",
        " TRUE - the command will be allowed if player has false permission state",
        " PERMISSION - the command will be allowed if player has true permission state"
    })
    public static class COMMAND_PERMISSION_STATE {
      @Comment("Permission: limbofilter.admin.sendfilter")
      public CommandPermissionState SEND_FILTER = CommandPermissionState.PERMISSION;
      @Comment("Permission: limbofilter.admin.reload")
      public CommandPermissionState RELOAD = CommandPermissionState.PERMISSION;
      @Comment("Permission: limbofilter.admin.stats")
      public CommandPermissionState STATS = CommandPermissionState.PERMISSION;
      @Comment("Permission: limbofilter.admin.help")
      public CommandPermissionState HELP = CommandPermissionState.TRUE;
    }

    @Create
    public MAIN.STRINGS STRINGS;

    @Comment("Leave title fields empty to disable.")
    public static class STRINGS {

      public String RELOAD = "{PRFX} &aReloaded successfully!";

      public String CLIENT_SETTINGS_KICK = "{PRFX}{NL}&cYour client doesn't send settings packets.";
      public String CLIENT_BRAND_KICK = "{PRFX}{NL}&cYour client doesn't send brand packet or it's blocked.";
      public String PROXY_CHECK_KICK = "{PRFX}{NL}&cYour connection is suspicious.";

      public String CHECKING_CHAT = "{PRFX} Bot-Filter check was started, please wait and don't move..";
      public String CHECKING_TITLE = "{PRFX}";
      public String CHECKING_SUBTITLE = "&aPlease wait..";

      public String CHECKING_CAPTCHA_CHAT = "{PRFX} &aPlease, solve the captcha, you have &6{0} &aattempts.";
      public String CHECKING_WRONG_CAPTCHA_CHAT = "{PRFX} &cYou have entered the captcha incorrectly, you have &6{0} &cattempts left.";
      public String CHECKING_CAPTCHA_TITLE = "&aPlease solve the captcha.";
      public String CHECKING_CAPTCHA_SUBTITLE = "&aYou have &6{0} &aattempts.";

      public String SUCCESSFUL_CRACKED = "{PRFX} &aSuccessfully passed the Bot-Filter check.";
      public String SUCCESSFUL_PREMIUM_KICK = "{PRFX}{NL}&aSuccessfully passed Bot-Filter check.{NL}&6Please, rejoin the server!";

      public String CAPTCHA_FAILED_KICK = "{PRFX}{NL}&cYou've mistaken in captcha check.{NL}&6Please, rejoin the server.";
      public String FALLING_CHECK_FAILED_KICK = "{PRFX}{NL}&cFalling Check was failed.{NL}&6Please, rejoin the server.";
      public String TIMES_UP = "{PRFX}{NL}&cYou have exceeded the maximum Bot-Filter check time.{NL}&6Please, rejoin the server.";

      public String STATS_FORMAT = "&c&lTotal Blocked: &6&l{0} &c&l| Connections: &6&l{1}s &c&l| Pings: &6&l{2}s &c&l| Total Connections: &6&l{3} &c&l| L7 Ping: &6&l{4} &c&l| L4 Ping: &6&l{5}";
      public String STATS_ENABLED = "{PRFX} &aNow you may see statistics in your action bar.";
      public String STATS_DISABLED = "{PRFX} &cYou can no longer see statistics in your action bar.";

      public String SEND_PLAYER_SUCCESSFUL = "{PRFX} Successfully sent {0} to the filter limbo.";
      public String SEND_SERVER_SUCCESSFUL = "{PRFX} Successfully sent {0} players from {1} to filter limbo.";
      public String SEND_FAILED = "{PRFX} There is no registered servers or connected players named {0}.";

      public String CAPTCHA_NOT_READY_YET = "{PRFX} The captcha is not ready yet. Try again in a few seconds.";
    }
  }
}
