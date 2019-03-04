package cn.yescallop.essentialsnk;

import cn.nukkit.AdventureSettings;
import cn.nukkit.IPlayer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.command.CommandSender;
import cn.nukkit.entity.weather.EntityLightning;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemArmor;
import cn.nukkit.item.ItemTool;
import cn.nukkit.level.Location;
import cn.nukkit.level.Position;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.DoubleTag;
import cn.nukkit.nbt.tag.FloatTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.permission.PermissionAttachmentInfo;
import cn.nukkit.plugin.PluginLogger;
import cn.nukkit.utils.Config;
import cn.nukkit.utils.ConfigSection;

import java.io.File;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EssentialsAPI {

    private static final Pattern COOLDOWN_PATTERN = Pattern.compile("^essentialsnk\\.cooldown\\.([0-9]+)$");
    private static final Pattern TP_COOLDOWN_PATTERN = Pattern.compile("^essentialsnk\\.tp\\.cooldown\\.([0-9]+)$");
    public static final Integer[] NON_SOLID_BLOCKS = new Integer[]{Block.AIR, Block.SAPLING, Block.WATER, Block.STILL_WATER, Block.LAVA, Block.STILL_LAVA, Block.COBWEB, Block.TALL_GRASS, Block.BUSH, Block.DANDELION,
            Block.POPPY, Block.BROWN_MUSHROOM, Block.RED_MUSHROOM, Block.TORCH, Block.FIRE, Block.WHEAT_BLOCK, Block.SIGN_POST, Block.WALL_SIGN, Block.SUGARCANE_BLOCK,
            Block.PUMPKIN_STEM, Block.MELON_STEM, Block.VINE, Block.CARROT_BLOCK, Block.POTATO_BLOCK, Block.DOUBLE_PLANT};
    private static EssentialsAPI instance = null;
    private static Duration THIRTY_DAYS = Duration.ZERO.plusDays(30);
    private Vector3 temporalVector = new Vector3();
    private EssentialsNK plugin;
    private final Map<CommandSender, Long> cooldown = new HashMap<>();
    private final List<TPCooldown> tpCooldowns = new ArrayList<>();
    private Map<Player, Location> playerLastLocation = new HashMap<>();
    private Map<Integer, TPRequest> tpRequests = new HashMap<>();
    private List<Player> vanishedPlayers = new ArrayList<>();
    private Config homeConfig;
    private Config warpConfig;
    private Config muteConfig;
    private Config ignoreConfig;

    public EssentialsAPI(EssentialsNK plugin) {
        instance = this;
        this.plugin = plugin;
        this.homeConfig = new Config(new File(plugin.getDataFolder(), "home.yml"), Config.YAML);
        this.warpConfig = new Config(new File(plugin.getDataFolder(), "warp.yml"), Config.YAML);
        this.muteConfig = new Config(new File(plugin.getDataFolder(), "mute.yml"), Config.YAML);
        this.ignoreConfig = new Config(new File(plugin.getDataFolder(), "ignore.yml"), Config.YAML);
    }

    public static EssentialsAPI getInstance() {
        return instance;
    }

    public Server getServer() {
        return plugin.getServer();
    }

    public PluginLogger getLogger() {
        return this.plugin.getLogger();
    }

    public void setLastLocation(Player player, Location pos) {
        this.playerLastLocation.put(player, pos);
    }

    public Location getLastLocation(Player player) {
        return this.playerLastLocation.get(player);
    }

    public boolean hasCooldown(CommandSender sender) {
        long cooldown = Long.MAX_VALUE;
        for (PermissionAttachmentInfo info : sender.getEffectivePermissions().values()) {
            Matcher matcher = COOLDOWN_PATTERN.matcher(info.getPermission().toLowerCase());
            if (matcher.find()) {
                int time = Integer.parseInt(matcher.group(1));
                if (time < cooldown) {
                    cooldown = time;
                }
            }
        }

        if (!sender.isOp() && cooldown < Long.MAX_VALUE) {
            long currentTime = System.currentTimeMillis();
            long lastCooldown = this.cooldown.getOrDefault(sender, -1L) + TimeUnit.SECONDS.toMillis(cooldown);

            if (currentTime > lastCooldown) {
                this.cooldown.put(sender, currentTime);
            } else {
                long timeLeft = TimeUnit.MILLISECONDS.toSeconds(lastCooldown - currentTime);
                sender.sendMessage(Language.translate("commands.generic.cooldown", timeLeft));
                return true;
            }
        }
        return false;
    }

    private OptionalInt hasTPCooldown(Player player) {
        int cooldown = Integer.MAX_VALUE;
        for (PermissionAttachmentInfo info : player.getEffectivePermissions().values()) {
            Matcher matcher = TP_COOLDOWN_PATTERN.matcher(info.getPermission().toLowerCase());
            if (matcher.find()) {
                int time = Integer.parseInt(matcher.group(1));
                if (time < cooldown) {
                    cooldown = time;
                }
            }
        }

        if (!player.isOp() && cooldown < Integer.MAX_VALUE) {
            return OptionalInt.of(cooldown);
        }
        return OptionalInt.empty();
    }

    public void onTP(Player player, Position position, String message) {
        OptionalInt cooldown = hasTPCooldown(player);

        if (cooldown.isPresent()) {
            player.sendMessage(Language.translate("commands.generic.teleporation.cooldown", cooldown.getAsInt()));
            tpCooldowns.add(new TPCooldown(player, position,
                    System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(cooldown.getAsInt()), message));
        } else {
            player.teleport(position);
            player.sendMessage(message);
        }
    }

    public List<TPCooldown> getTpCooldowns() {
        return tpCooldowns;
    }

    public boolean switchCanFly(Player player) {
        boolean canFly = !this.canFly(player);
        this.setCanFly(player, canFly);
        return canFly;
    }

    public boolean canFly(Player player) {
        return player.getAdventureSettings().get(AdventureSettings.Type.ALLOW_FLIGHT);
    }

    public void setCanFly(Player player, boolean canFly) {
        player.getAdventureSettings().set(AdventureSettings.Type.ALLOW_FLIGHT, canFly);
        player.getAdventureSettings().update();
    }

    public boolean switchVanish(Player player) {
        boolean vanished = this.isVanished(player);
        if (vanished) {
            this.setVanished(player, false);
            vanishedPlayers.remove(player);
        } else {
            this.setVanished(player, true);
            vanishedPlayers.add(player);
        }
        return !vanished;
    }

    public boolean isVanished(Player player) {
        return vanishedPlayers.contains(player);
    }

    public void setVanished(Player player, boolean vanished) {
        for (Player p : this.getServer().getOnlinePlayers().values()) {
            if (vanished) {
                p.hidePlayer(player);
            } else {
                p.showPlayer(player);
            }
        }
    }

    public boolean isRepairable(Item item) {
        return item instanceof ItemTool || item instanceof ItemArmor;
    }

    public void strikeLighting(Position pos) {
        FullChunk chunk = pos.getLevel().getChunk((int) pos.getX() >> 4, (int) pos.getZ() >> 4);
        CompoundTag nbt = new CompoundTag()
                .putList(new ListTag<DoubleTag>("Pos")
                        .add(new DoubleTag("", pos.getX()))
                        .add(new DoubleTag("", pos.getY()))
                        .add(new DoubleTag("", pos.getZ())))
                .putList(new ListTag<DoubleTag>("Motion")
                        .add(new DoubleTag("", 0))
                        .add(new DoubleTag("", 0))
                        .add(new DoubleTag("", 0)))
                .putList(new ListTag<FloatTag>("Rotation")
                        .add(new FloatTag("", 0))
                        .add(new FloatTag("", 0)));
        EntityLightning lightning = new EntityLightning(chunk, nbt);
        lightning.spawnToAll();
    }

    private static int getHashCode(Player from, Player to, boolean isTo) {
        return from.hashCode() + to.hashCode() + Boolean.hashCode(isTo);
    }

    public void requestTP(Player from, Player to, boolean isTo) {
        this.tpRequests.put(getHashCode(from, to, isTo), new TPRequest(System.currentTimeMillis(), from, to, isTo));
    }

    public TPRequest getLatestTPRequestTo(Player player) {
        TPRequest latest = null;
        for (TPRequest request : this.tpRequests.values()) {
            if (request.getTo() == player && (latest == null || request.getStartTime() > latest.getStartTime())) {
                latest = request;
            }
        }
        return latest;
    }

    public TPRequest getTPRequestBetween(Player from, Player to) {
        int key;
        if (this.tpRequests.containsKey(key = getHashCode(from, to, true)) || this.tpRequests.containsKey(key = getHashCode(from, to, false))) {
            return this.tpRequests.get(key);
        }
        return null;
    }

    public boolean hasTPRequestBetween(Player from, Player to) {
        return this.tpRequests.containsKey(getHashCode(from, to, true)) || this.tpRequests.containsKey(getHashCode(from, to, false));
    }

    public void removeTPRequestBetween(Player from, Player to) {
        this.tpRequests.remove(getHashCode(from, to, true));
        this.tpRequests.remove(getHashCode(from, to, false));

    }

    public void removeTPRequest(Player player) {
        for (Map.Entry<Integer, TPRequest> entry : this.tpRequests.entrySet()) {
            TPRequest request = entry.getValue();
            if (request.getFrom() == player || request.getTo() == player) {
                this.tpRequests.remove(entry.getKey());
            }
        }
    }

    public boolean ignore(UUID player, UUID toIgnore) {
        this.ignoreConfig.reload();
        String playerId = player.toString();
        String toIgnoreId = toIgnore.toString();
        Map<String, Object> ignores = this.ignoreConfig.get(playerId, new HashMap<>());
        boolean add = !ignores.containsKey(toIgnoreId);
        if (add) {
            ignores.put(toIgnoreId, null);
        } else {
            ignores.remove(toIgnoreId);
        }
        this.ignoreConfig.set(playerId, ignores);
        this.ignoreConfig.save();
        return add;
    }

    public boolean isIgnoring(UUID player, UUID target) {
        String playerId = player.toString();
        String targetId = target.toString();

        Map<String, Object> ignores = this.ignoreConfig.get(playerId, null);

        return ignores != null && ignores.containsKey(targetId);
    }

    public boolean setHome(IPlayer player, String name, Location pos) {
        return setHome(player.getUniqueId(), name, pos);
    }

    public boolean setHome(UUID uuid, String name, Location location) {
        this.homeConfig.reload();
        checkAndUpdateLegacyHomes(uuid);
        Map<String, Object> map = getHomeMap(uuid, true);

        boolean replaced = map.containsKey(name);
        List home = Arrays.asList(location.level.getName(), location.x, location.y, location.z, location.yaw, location.pitch);
        map.put(name, home);
        this.homeConfig.save();
        return replaced;
    }

    public Location getHome(IPlayer player, String name) {
        return getHome(player.getUniqueId(), name);
    }

    public Location getHome(UUID uuid, String name) {
        this.homeConfig.reload();
        checkAndUpdateLegacyHomes(uuid);
        @SuppressWarnings("unchecked") Map<String, List<Object>> map = (Map) getHomeMap(uuid, false);
        List<Object> home = map.get(name);
        if (home == null || home.size() != 6) {
            return null;
        }
        return new Location((double) home.get(1), (double) home.get(2), (double) home.get(3), (double) home.get(4), (double) home.get(5), this.getServer().getLevelByName((String) home.get(0)));
    }

    public void removeHome(IPlayer player, String name) {
        removeHome(player.getUniqueId(), name);
    }

    public void removeHome(UUID uuid, String name) {
        this.homeConfig.reload();
        checkAndUpdateLegacyHomes(uuid);
        getHomeMap(uuid, true).remove(name);
        this.homeConfig.save();
    }

    public String[] getHomesList(IPlayer player) {
        return getHomesList(player.getUniqueId());
    }

    public String[] getHomesList(UUID uuid) {
        this.homeConfig.reload();
        checkAndUpdateLegacyHomes(uuid);
        String[] list = getHomeMap(uuid, false).keySet().toArray(new String[0]);
        Arrays.sort(list, String.CASE_INSENSITIVE_ORDER);
        return list;
    }

    public boolean isHomeExists(IPlayer player, String name) {
        return isHomeExists(player.getUniqueId(), name);
    }

    public boolean isHomeExists(UUID uuid, String name) {
        this.homeConfig.reload();
        checkAndUpdateLegacyHomes(uuid);
        return getHomeMap(uuid, false).containsKey(name);
    }

    private Map<String, Object> getHomeMap(UUID uuid, boolean set) {
        ConfigSection section = this.homeConfig.getSection(uuid.toString());
        if (set) {
            this.homeConfig.set(uuid.toString(), section);
        }
        return section;
    }

    private void checkAndUpdateLegacyHomes(UUID uuid) {
        IPlayer player = getServer().getOfflinePlayer(uuid);
        if (player == null) {
            return;
        }
        String uuidString = player.getUniqueId().toString();
        String name = player.getName().toLowerCase();
        if (this.homeConfig.exists(name)) {
            if (this.homeConfig.exists(uuidString)) {
                ConfigSection section = this.homeConfig.getSection(uuidString);
                this.homeConfig.getSection(name).forEach((s, o) -> {
                    if (!section.containsKey(s)) {
                        section.put(s, o);
                    }
                });
                this.homeConfig.remove(name);
            } else {
                this.homeConfig.set(uuidString, this.homeConfig.get(name));
            }
        }
    }

    public boolean setWarp(String name, Location pos) {
        this.warpConfig.reload();
        boolean replaced = warpConfig.exists(name);
        Object[] home = new Object[]{pos.level.getName(), pos.x, pos.y, pos.z, pos.yaw, pos.pitch};
        this.warpConfig.set(name, home);
        this.warpConfig.save();
        return replaced;
    }

    public Location getWarp(String name) {
        this.warpConfig.reload();
        List warp = this.warpConfig.getList(name);
        if (warp == null || warp.size() != 6) {
            return null;
        }
        return new Location((double) warp.get(1), (double) warp.get(2), (double) warp.get(3), (double) warp.get(4), (double) warp.get(5), this.getServer().getLevelByName((String) warp.get(0)));
    }

    public void removeWarp(String name) {
        this.warpConfig.reload();
        this.warpConfig.remove(name);
        this.warpConfig.save();
    }

    public String[] getWarpsList() {
        this.warpConfig.reload();
        String[] list = this.warpConfig.getKeys().toArray(new String[0]);
        Arrays.sort(list, String.CASE_INSENSITIVE_ORDER);
        return list;
    }

    public boolean isWarpExists(String name) {
        this.warpConfig.reload();
        return this.warpConfig.exists(name);
    }

    public Position getStandablePositionAt(Position pos) {
        int x = pos.getFloorX();
        int y = pos.getFloorY() + 1;
        int z = pos.getFloorZ();
        for (; y <= 128; y++) {
            if (!pos.level.getBlock(this.temporalVector.setComponents(x, y, z)).isSolid() && !pos.level.getBlock(this.temporalVector.setComponents(x, y + 1, z)).isSolid()) {
                return new Position(x + 0.5, pos.level.getBlock(this.temporalVector.setComponents(x, y - 1, z)).getBoundingBox().getMaxY(), z + 0.5, pos.level);
            }
        }
        return null;
    }

    public Position getHighestStandablePositionAt(Position pos) {
        int x = pos.getFloorX();
        int z = pos.getFloorZ();
        for (int y = 127; y >= 0; y--) {
            if (pos.level.getBlock(this.temporalVector.setComponents(x, y, z)).isSolid()) {
                return new Position(x + 0.5, pos.level.getBlock(this.temporalVector.setComponents(x, y, z)).getBoundingBox().getMaxY(), z + 0.5, pos.level);
            }
        }
        return null;
    }

    //for peace
    public boolean mute(IPlayer player, int d, int h, int m, int s) {
        return this.mute(player, Duration.ZERO.plusDays(d).plusHours(h).plusMinutes(m).plusSeconds(s));
    }

    //for peace too -- lmlstarqaq
    public boolean mute(IPlayer player, Duration duration) {
        return mute(player.getUniqueId(), duration);
    }

    public boolean mute(UUID uuid, int d, int h, int m, int s) {
        return this.mute(uuid, Duration.ZERO.plusDays(d).plusHours(h).plusMinutes(m).plusSeconds(s));
    }

    public boolean mute(UUID uuid, Duration duration) {
        checkAndUpdateLegacyMute(uuid);
        if (duration.isNegative() || duration.isZero()) return false;
        // t>30 => (t!=30 && t>=30) => (t!=30 && t-30>=0) => (t!=30 && !(t-30<0))
        if (duration.toDays() != 30 && !(duration.minus(THIRTY_DAYS).isNegative())) return false; // t>30
        this.muteConfig.set(uuid.toString(), Timestamp.valueOf(LocalDateTime.now().plus(duration)).getTime() / 1000);
        this.muteConfig.save();
        return true;
    }

    public Integer getRemainingTimeToUnmute(IPlayer player) {
        return getRemainingTimeToUnmute(player.getUniqueId());
    }

    public Integer getRemainingTimeToUnmute(UUID uuid) {
        this.muteConfig.reload();
        checkAndUpdateLegacyMute(uuid);
        Integer time = (Integer) this.muteConfig.get(uuid.toString());
        return time == null ? null : (int) (time - Timestamp.valueOf(LocalDateTime.now()).getTime() / 1000);
    }

    public boolean isMuted(IPlayer player) {
        return isMuted(player.getUniqueId());
    }

    public boolean isMuted(UUID uuid) {
        this.muteConfig.reload();
        checkAndUpdateLegacyMute(uuid);
        Integer time = this.getRemainingTimeToUnmute(uuid);
        if (time == null) return false;
        if (time <= 0) {
            this.unmute(uuid);
            return false;
        }
        return true;
    }

    public String getMuteTimeMessage(int d, int h, int m, int s) {
        return getDurationString(Duration.ZERO.plusDays(d).plusHours(h).plusMinutes(m).plusSeconds(s));
    }

    public String getUnmuteTimeMessage(IPlayer player) {
        Integer time = this.getRemainingTimeToUnmute(player);
        return getDurationString(Duration.ofSeconds(time));
    }

    public void unmute(IPlayer player) {
        unmute(player.getUniqueId());
    }

    public void unmute(UUID uuid) {
        checkAndUpdateLegacyMute(uuid);
        this.muteConfig.remove(uuid.toString());
        this.muteConfig.save();
    }

    private void checkAndUpdateLegacyMute(UUID uuid) {
        IPlayer player = getServer().getOfflinePlayer(uuid);
        if (player == null) {
            return;
        }
        String uuidString = player.getUniqueId().toString();
        String name = player.getName().toLowerCase();
        if (this.muteConfig.exists(name)) {
            if (this.muteConfig.exists(uuidString)) {
                ConfigSection section = this.muteConfig.getSection(uuidString);
                this.muteConfig.getSection(name).forEach((s, o) -> {
                    if (!section.containsKey(s)) {
                        section.put(s, o);
                    }
                });
                this.homeConfig.remove(name);
            } else {
                this.muteConfig.set(uuidString, this.muteConfig.get(name));
            }
        }
    }

    // Scallop: Thanks lmlstarqaq
    // %0 days %1 hours %2 minutes %3 seconds, language localized.
    public String getDurationString(Duration duration) {
        if (duration == null) return "null";
        long d = duration.toDays();
        long h = duration.toHours() % 24;
        long m = duration.toMinutes() % 60;
        long s = duration.getSeconds() % 60;
        String d1 = "", h1 = "", m1 = "", s1 = "";
        //Singulars and plurals. Maybe necessary for English or other languages. 虽然中文似乎没有名词的单复数 -- lmlstarqaq
        if (d > 1) d1 = Language.translate("commands.generic.days", d);
        else if (d > 0) d1 = Language.translate("commands.generic.day", d);
        if (h > 1) h1 = Language.translate("commands.generic.hours", h);
        else if (h > 0) h1 = Language.translate("commands.generic.hour", h);
        if (m > 1) m1 = Language.translate("commands.generic.minutes", m);
        else if (m > 0) m1 = Language.translate("commands.generic.minute", m);
        if (s > 1) s1 = Language.translate("commands.generic.seconds", s);
        else if (s > 0) s1 = Language.translate("commands.generic.second", s);
        //In some languages, times are read from SECONDS to HOURS, which should be noticed.
        return Language.translate("commands.generic.time.format", d1, h1, m1, s1).trim().replaceAll(" +", " ");
    }
}