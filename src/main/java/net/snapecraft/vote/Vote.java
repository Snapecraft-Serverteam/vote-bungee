package net.snapecraft.vote;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.vexsoftware.votifier.bungee.events.VotifierEvent;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;

public final class Vote extends Plugin implements Listener {

    @Override
    public void onEnable() {
        getProxy().getPluginManager().registerListener(this, this);
        getProxy().registerChannel("vote:votechannel");
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    @EventHandler
    public void vote(VotifierEvent event) {
        vote(event.getVote());
    }

    private void vote(com.vexsoftware.votifier.model.Vote vote) {
        for (ProxiedPlayer p : getProxy().getPlayers()) {
            p.sendMessage(new TextComponent("§2Der Spieler §6" + vote.getUsername() + " §2hat gevotet."));
        }
        ProxiedPlayer target = getProxy().getPlayer(vote.getUsername());
        if(target != null) {
            sendCustomData(target, target.getName());
        } else{
            //TODO: Not Online --> MySQL
        }
    }


    private void sendCustomData(ProxiedPlayer player, String data1) {
        if (ProxyServer.getInstance().getPlayers() == null || ProxyServer.getInstance().getPlayers().isEmpty()) {
            return;
        }
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("VoteAlert"); // the channel could be whatever you want
        out.writeUTF(data1); // this data could be whatever you want

        player.getServer().sendData("vote:votechannel", out.toByteArray()); // we send the data to the server
    }

}
