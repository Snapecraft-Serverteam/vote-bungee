package net.snapecraft.vote;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.*;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

public class VoteCMD extends Command {
    public VoteCMD(String name) {
        super(name);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if(args.length == 0) {
            ComponentBuilder b1 = new ComponentBuilder("=========== ").color(ChatColor.WHITE).append("Vote").color(ChatColor.BLUE).append(" ===========").color(ChatColor.WHITE);
            ComponentBuilder b2 = new ComponentBuilder("Du möchtest Voten? ").color(ChatColor.WHITE);
            TextComponent b3 = new TextComponent(b2.create());
            TextComponent b31 = new TextComponent("Klicke Hier!");
            b31.setBold(true);
            b31.setColor(ChatColor.getByChar('a'));
            b31.setClickEvent( new ClickEvent( ClickEvent.Action.OPEN_URL, "https://snapecraft.net/voten" ) );
            b31.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Öffnet die Vote Seite").color(ChatColor.AQUA).bold(true).create()));
            b3.addExtra(b31);

            sender.sendMessage(b1.create());
            sender.sendMessage(b3);

            if(sender instanceof ProxiedPlayer) {
                VoteObject vote = Vote.getInstance().getVotes((ProxiedPlayer) sender);
                int count = vote.getVotes();
                int coins = vote.getCoins();
                TextComponent c4 = new TextComponent(
                        ChatColor.LIGHT_PURPLE + "Du hast schon " +
                        ChatColor.GOLD + ChatColor.BOLD + Integer.toString(count) + ChatColor.RESET + ChatColor.LIGHT_PURPLE +
                        " mal gevoted.\n" + ChatColor.AQUA + "Votecoins: " + ChatColor.BOLD + ChatColor.GOLD + Integer.toString(coins)
                );
                sender.sendMessage(c4);
            }

        } else {
            ComponentBuilder b = new ComponentBuilder("Usage: ").color(ChatColor.RED).append("/vote").color(ChatColor.DARK_RED);
            sender.sendMessage(b.create());
        }
    }
}
