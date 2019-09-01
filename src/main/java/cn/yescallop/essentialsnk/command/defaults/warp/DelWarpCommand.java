package cn.yescallop.essentialsnk.command.defaults.warp;

import cn.nukkit.command.CommandSender;
import cn.nukkit.command.data.CommandParamType;
import cn.nukkit.command.data.CommandParameter;
import cn.nukkit.utils.TextFormat;
import cn.yescallop.essentialsnk.EssentialsAPI;
import cn.yescallop.essentialsnk.Language;
import cn.yescallop.essentialsnk.command.CommandBase;

public class DelWarpCommand extends CommandBase {

    public DelWarpCommand(EssentialsAPI api) {
        super("delwarp", api);
        this.setAliases(new String[]{"remwarp", "rmwarp"});

        // command parameters
        commandParameters.clear();
        this.commandParameters.put("default", new CommandParameter[] {
                new CommandParameter("warp", CommandParamType.TEXT, false)
        });
    }

    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!this.testPermission(sender)) {
            return false;
        }
        if (args.length != 1) {
            this.sendUsage(sender);
            return false;
        }
        if (!api.isWarpExists(args[0].toLowerCase())) {
            sender.sendMessage(TextFormat.RED + Language.translate("commands.warp.notexists", args[0]));
            return false;
        }
        api.removeWarp(args[0].toLowerCase());
        sender.sendMessage(Language.translate("commands.delwarp.success", args[0]));
        return true;
    }
}
