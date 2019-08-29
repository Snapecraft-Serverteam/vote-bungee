package net.snapecraft.vote;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.vexsoftware.votifier.bungee.events.VotifierEvent;
import de.tallerik.MySQL;
import de.tallerik.utils.Result;
import de.tallerik.utils.Row;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import net.md_5.bungee.event.EventHandler;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.URL;
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
        initConfig();
        initMysql();
        getProxy().getPluginManager().registerListener(this, this);
        getProxy().registerChannel("vote:votechannel");
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
        // Plugin shutdown logic
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
                            System.out.println("Adedd TempVote to Database");
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
                            "  ON DUPLICATE KEY UPDATE count=count+1;";
                    if(mySQL.custom(sql)) {
                        System.out.println("Adedd Vote to Database");
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
        SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
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


    public int getVotes(ProxiedPlayer p) {
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

    public static Vote getInstance() {
        return instance;
    }


}
