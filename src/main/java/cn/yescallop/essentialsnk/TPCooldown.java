package cn.yescallop.essentialsnk;

import cn.nukkit.Player;
import cn.nukkit.level.Position;

public class TPCooldown {
    private final Player player;
    private final Position position;
    private final long timestamp;
    private final String message;

    public TPCooldown(Player player, Position position, long timestamp, String message) {
        this.player = player;
        this.position = position;
        this.timestamp = timestamp;
        this.message = message;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void execute() {
        player.teleport(position);
        player.sendMessage(message);
    }
}
