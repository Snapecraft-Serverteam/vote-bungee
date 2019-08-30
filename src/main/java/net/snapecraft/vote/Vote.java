package net.snapecraft.vote;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.vexsoftware.votifier.bungee.events.VotifierEvent;
import de.tallerik.MySQL;
import de.tallerik.utils.Result;
import de.tallerik.utils.Row;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.scheduler.ScheduledTask;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import net.md_5.bungee.event.EventHandler;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class Vote extends Plugin implements Listener {

    private MySQL mySQL;
    private boolean mysqlReady = false;
    private boolean mysqlInit = false;
    private Configuration configuration;
    private static Vote instance;
    private static HashMap<String, String> players = new HashMap<>();

    @Override
    public void onEnable() {
        instance = this;
        getProxy().getPluginManager().registerCommand(this, new VoteCMD("vote"));
        getProxy().getPluginManager().registerCommand(this, new VoteMSGCMD("votemsg"));
        initConfig();
        initMysql();
        initTimer();
        getProxy().getPluginManager().registerListener(this, this);
        getProxy().registerChannel("vote:votechannel");
    }

    private void initTimer() {

        ScheduledTask task = getProxy().getScheduler().schedule(this, () -> {
            for(ServerInfo serverInfo : ProxyServer.getInstance().getServers().values()) {
                for (ProxiedPlayer pp : serverInfo.getPlayers()) {
                    pp.sendMessage(getVoteMessage(pp));
                }
            }
        }, 1, 15, TimeUnit.MINUTES);

    }

    private void initMysql() {
        mySQL = new MySQL();
        mySQL.setHost(configuration.getString("SQL.host"));
        mySQL.setPort(configuration.getInt("SQL.port"));
        mySQL.setUser(configuration.getString("SQL.user"));
        mySQL.setPassword(configuration.getString("SQL.pw"));
        mySQL.setDb(configuration.getString("SQL.database"));
        if(!mySQL.connect()) {
            System.out.println("[VOTE] MYSQL ERROR");
        }
        mysqlReady = mySQL.isConnected();
        if(mysqlReady) {
            String sql1 = "CREATE TABLE IF NOT EXISTS `vote_count`" +
                    "( `id` INT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
                    " `uuid` VARCHAR(40) NOT NULL UNIQUE, " +
                    "`count` INT NOT NULL , " +
                    "`lastvote` DATE NOT NULL);";

            String sql2 = "CREATE TABLE IF NOT EXISTS `vote_temp` (" +
                    " `id` INT NOT NULL AUTO_INCREMENT PRIMARY KEY, " +
                    "`uuid` VARCHAR(40) NOT NULL UNIQUE, " +
                    "`count` INT NOT NULL);";
            mySQL.custom(sql1);
            mySQL.custom(sql2);
        }
        mysqlInit = true;
    }

    private void initConfig() {
        try {
            configuration  = ConfigurationProvider.getProvider(YamlConfiguration.class).load(new File(getDataFolder(), "config.yml"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveConfig() {
        try {
            ConfigurationProvider.getProvider(YamlConfiguration.class).save(configuration, new File(getDataFolder(), "config.yml"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean isReady() {
        if(!mysqlInit) {
            return false;
        }
        if(mySQL.isConnected()) {
            return true;
        } else {
            return mySQL.connect();
        }
    }

    @Override
    public void onDisable() {
        mySQL.close();
    }

    @EventHandler
    public void vote(VotifierEvent event) {
        vote(event.getVote());
    }

    @EventHandler
    public void join(PostLoginEvent event) {
        if(isReady()) {
            Result result = mySQL.rowSelect("vote_temp", "count", "`uuid`='" + getUUID(event.getPlayer().getUniqueId()) + "'");
            int count = 0;
            List<Row> rows = result.getRows();
            for(Row r : rows) {
                count += (int)r.get("count");
            }
            if(count == 0) {
                return;
            } else {

                for (int i = 0; i < count; i++) {
                    getProxy().getScheduler().schedule(this, new Runnable() {
                        @Override
                        public void run() {
                            sendCustomData(event.getPlayer(), event.getPlayer().getName());
                        }
                    }, 10, TimeUnit.SECONDS);
                }
                mySQL.custom("DELETE FROM `vote_temp` WHERE `uuid` = '" + getUUID(event.getPlayer().getUniqueId()) + "'");
            }

        } else {
            System.out.println("Player joined! But SQL is not here...");
        }

    }

    public void vote(com.vexsoftware.votifier.model.Vote vote) {
        for (ProxiedPlayer p : getProxy().getPlayers()) {
            p.sendMessage(new TextComponent("§2Der Spieler §6" + vote.getUsername() + " §2hat gevotet."));
        }
        ProxiedPlayer target = getProxy().getPlayer(vote.getUsername());
        if(target != null) {
            sendCustomData(target, target.getName());
        } else{
            if(isReady()) {
                try {
                    String uuid = getUUID(vote.getUsername());
                    if(uuid != null) {
                        String sql = "INSERT INTO vote_temp (uuid,count) VALUES ('"+ uuid + "', 1)" +
                                "  ON DUPLICATE KEY UPDATE count=count+1;";
                        if(mySQL.custom(sql)) {
                            System.out.println("Added TempVote to Database");
                        } else {
                            System.out.println("Vote adding failed in vote_temp!");
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
            System.out.println("Offline Vote! But SQL is not here...");
            }

        }

        // Add Vote to database
        if(isReady()) {
            try {
                String  uuid;
                if(target != null) {
                    uuid = getUUID(target.getUniqueId());
                } else {
                    uuid = getUUID(vote.getUsername());
                }

                if(uuid != null) {
                    String now = getCurrentTimeStamp();
                    String sql = "INSERT INTO vote_count (uuid,count,lastvote) VALUES ('"+ uuid + "', 1, '"+now+"')" +
                            "  ON DUPLICATE KEY UPDATE count=count+1 AND lastvote='" + now + "';";
                    if(mySQL.custom(sql)) {
                        System.out.println("Added Vote to Database");
                    } else {
                        System.out.println("Vote adding failed in vote_count!");
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        } else {
            System.out.println("Vote! But SQL is not here...");
        }
    }

    private String getUUID(String name) throws IOException {
        if(players.get(name) != null) {
            return players.get(name);
        }
        String mojangBaseURL = "https://api.mojang.com/users/profiles/minecraft/";
        String out = new Scanner(new URL(mojangBaseURL + name).openStream(), "UTF-8").useDelimiter("\\A").next();

        try {
            JSONObject obj = new JSONObject(out);
            String id = obj.getString("id");
            if(id == null || id.equals("")) {
                return null;
            } else {
                players.put(obj.getString("name"), obj.getString("id"));
                return id;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private String getUUID(UUID id) {
        return id.toString().replaceAll("-", "");
    }

    private static String getCurrentTimeStamp() {
        SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd");
        Date now = new Date();
        return sdfDate.format(now);
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


    public int getVoteCount(ProxiedPlayer p) {
        if(isReady()) {
            Result result = mySQL.rowSelect("vote_count", "count", "`uuid`='" + getUUID(p.getUniqueId()) + "'");

            java.util.List<Row> rows = result.getRows();
            int count = 0;

            for (Row r:
                 rows) {
                count += (int) r.get("count");
            }
            return count;
        } else {
            return -1;
        }
    }

    public VoteObject getVotes(ProxiedPlayer p) {
        if(isReady()) {
            Result result = mySQL.rowSelect("vote_count", "count, lastvote", "`uuid`='" + getUUID(p.getUniqueId()) + "'");

            java.util.List<Row> rows = result.getRows();
            try {
                Row r = rows.get(0); // Only one shout exists
                return new VoteObject(getUUID(p.getUniqueId()), (int)r.get("count"), (Date) r.get("lastvote"));
            } catch (IndexOutOfBoundsException e) {
                return null;
            }

        } else {
            return null;
        }
    }

    public static TextComponent getVoteMessage(ProxiedPlayer p) {
        VoteObject vote = Vote.getInstance().getVotes(p);
        try {
            TextComponent message1 = new TextComponent("Heute schon " + ChatColor.GREEN + "gevoted?\n");
            TextComponent message2 = new TextComponent(ChatColor.RESET + "Wenn du regelmäßig votest, " + ChatColor.RED + ChatColor.BOLD + "unterstützt " + ChatColor.RESET + "du den Server nicht nur, sondern bekommst auch " + ChatColor.RED + ChatColor.BOLD + "tolle Belohnungen!\n");
            TextComponent message3;
            if (vote == null) {
                message3 = new TextComponent(ChatColor.RESET + "Du hast noch nicht gevoted\n");
            } else {
                message3 = new TextComponent(ChatColor.RESET + "Du hast bereits " + ChatColor.GOLD + ChatColor.BOLD + vote.getVotes() + ChatColor.RESET + " mal gevoted");
            }

            DateFormat format = new SimpleDateFormat("yyyy-MM-dd");
            if (vote == null || !format.format(vote.getLast()).equals(format.format(new Date()))) {
                TextComponent message4 = new TextComponent(ChatColor.RESET + "\nZum Voten hier klicken: ");
                TextComponent message5 = new TextComponent("" + ChatColor.BLUE + ChatColor.BOLD + ChatColor.UNDERLINE + "Vote");
                message4.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://snapecraft.net/voten"));
                message4.addExtra(message5);
                message3.addExtra(message4);
            }
            message2.addExtra(message3);
            message1.addExtra(message2);
            return message1;
        } catch (NullPointerException e) {}

        return null;
    }


    public static Vote getInstance() {
        return instance;
    }


}
