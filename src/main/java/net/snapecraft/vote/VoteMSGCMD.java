package net.snapecraft.vote;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

public class VoteMSGCMD extends Command {
    public VoteMSGCMD(String name) {
        super(name);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        sender.sendMessage(Vote.getVoteMessage((ProxiedPlayer) sender));
    }
}
