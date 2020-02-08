package net.snapecraft.vote;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.vexsoftware.votifier.bungee.events.VotifierEvent;
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
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.sql.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class Vote extends Plugin implements Listener {

    private Connection mySQL;
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
        try {initMysql();} catch (Exception e) {
            e.printStackTrace();
        }
        initTimer();
        getProxy().getPluginManager().registerListener(this, this);
        getProxy().registerChannel("vote:votechannel");
    }

    /**
     * Adds Timer for sending Vote-advert
     */
    private void initTimer() {

        getProxy().getScheduler().schedule(this, () -> {
            for(ServerInfo serverInfo : ProxyServer.getInstance().getServers().values()) {
                for (ProxiedPlayer pp : serverInfo.getPlayers()) {
                    pp.sendMessage(getVoteMessage(pp));
                }
            }
        }, 1, 15, TimeUnit.MINUTES);

    }

    private void initMysql() throws ClassNotFoundException, SQLException {

        String host = configuration.getString("SQL.host");
        String user = configuration.getString("SQL.user");
        String pw = configuration.getString("SQL.pw");
        String db = configuration.getString("SQL.database");
        int port = configuration.getInt("SQL.port");

        Class.forName("com.mysql.jdbc.Driver");
        mySQL = DriverManager.getConnection("jdbc:mysql://" + host+ ":" + port + "/" + db, user, pw);

        if(mySQL.isClosed()) {
            System.out.println("[VOTE] MYSQL ERROR");
        }
        mysqlReady = !mySQL.isClosed();

        if(mysqlReady) {
            String sql1 = "CREATE TABLE IF NOT EXISTS `vote_count`" +
                    "( `id` INT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
                    " `uuid` VARCHAR(40) NOT NULL UNIQUE, " +
                    "`votecount` INT NOT NULL , " +
                    "`lastvote` DATE NOT NULL);";
            Statement sqlst = mySQL.createStatement();
            sqlst.executeUpdate(sql1);


            String sql2 = "CREATE TABLE IF NOT EXISTS `vote_points`" +
                    "( `id` INT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
                    " `uuid` VARCHAR(40) NOT NULL UNIQUE, " +
                    "`valuecount` INT NOT NULL);";

            sqlst.executeUpdate(sql2);
            sqlst.close();
        }
        mysqlInit = true;
    }

    private void initConfig() {
        File f = new File(getDataFolder(), "config.yml");
        if(!f.exists()) {
            try (InputStream in = getResourceAsStream("config.yml")) {
                Files.copy(in, f.toPath());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            configuration = ConfigurationProvider.getProvider(YamlConfiguration.class).load(f);
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

    private void reloadConfig() {
        File f = new File(getDataFolder(), "config.yml");
        try {
            configuration  = ConfigurationProvider.getProvider(YamlConfiguration.class).load(f);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean isReady() {
        if(!mysqlInit) {
            return false;
        }
        try {
            if(!mySQL.isClosed()) {
                return true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public void onDisable() {
        try {
            if(isReady())
                mySQL.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @EventHandler
    public void vote(VotifierEvent event) {
        vote(event.getVote());
    }

    @EventHandler
    public void join(PostLoginEvent event) {

    }

    public void vote(com.vexsoftware.votifier.model.Vote vote) {
        // Vote-Message serverwide
        for (ProxiedPlayer p : getProxy().getPlayers()) {
            p.sendMessage(new TextComponent("§2Der Spieler §6" + vote.getUsername() + " §2hat gevotet."));
        }

        // Plugin-Channel Message
        ProxiedPlayer target = getProxy().getPlayer(vote.getUsername());
        if(target != null) {
            sendCustomData(target, target.getName());
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

                    // Add Vote in vote_count
                    String now = getCurrentTimeStamp();
                    Statement stmt = mySQL.createStatement();
                    if(hasVoteEntry(uuid, "vote_count")) {
                        String sql = "UPDATE vote_count SET votecount = votecount + 1, lastvote = '" + now + "' WHERE uuid = '%s';";
                        sql = String.format(sql, uuid);
                        stmt.executeUpdate(sql);
                        stmt.close();
                    } else {
                        String sql = "INSERT INTO vote_count (uuid,votecount,lastvote) VALUES ('%s',1,'" + now + "');";
                        sql = String.format(sql, uuid);
                        stmt.executeUpdate(sql);
                        stmt.close();
                    }


                    // Add Vote in vote_points
                    int coins = configuration.getInt("Vote.votecoinsonvote");
                    Statement stmt2 = mySQL.createStatement();
                    if(hasVoteEntry(uuid, "vote_points")) {
                        String sql = "UPDATE vote_points SET valuecount = valuecount + " + coins + " WHERE uuid = '%s';";
                        sql = String.format(sql, uuid);
                        stmt2.executeUpdate(sql);
                        stmt2.close();
                    } else {
                        String sql = "INSERT INTO vote_points ( uuid, valuecount ) VALUES ('%s', " + coins + ");";
                        sql = String.format(sql, uuid);
                        stmt2.executeUpdate(sql);
                        stmt2.close();
                    }

                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        } else {
            System.out.println("Vote! But SQL is not here...");
        }
    }

    /**
     * @param uuid UUID-String of Player
     * @param table Which table shout be checked
     * @return Boolean if theres a entry for this uuid in Database
     */
    private boolean hasVoteEntry(String uuid, String table) {
        if(isReady()) {
            try {
                Statement stmt = mySQL.createStatement();
                String sql = "SELECT uuid FROM " + table + " WHERE uuid = '%s'";
                sql = String.format(sql, uuid.replaceAll("-", ""));
                ResultSet rs = stmt.executeQuery(sql);
                int i = 0;
                while (rs.next()) {
                    i++;
                }
                rs.close();
                stmt.close();
                return i != 0;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    /**
     *  Requesting mojangs api to get uuid of Player
     * @param name Username of the Player
     * @return Short UUID
     * @throws IOException Because of Webrequest
     */
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

    /**
     * @param id Spigots UUID Object
     * @return Player short UUID
     */
    private String getUUID(UUID id) {
        return id.toString().replaceAll("-", "");
    }

    private static String getCurrentTimeStamp() {
        SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd");
        Date now = new Date();
        return sdfDate.format(now);
    }


    /**
     * Sends Pluginchannel-Message to Spigot-Server
     * @param player Instance of {@link ProxiedPlayer} which is the target
     * @param data1 Informations about the vote
     */
    private void sendCustomData(ProxiedPlayer player, String data1) {
        if (ProxyServer.getInstance().getPlayers() == null || ProxyServer.getInstance().getPlayers().isEmpty()) {
            return;
        }
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("VoteAlert"); // the channel could be whatever you want
        out.writeUTF(data1); // this data could be whatever you want

        player.getServer().sendData("vote:votechannel", out.toByteArray()); // we send the data to the server
    }


    /**
     * Returns count of Votes
     * @param p Player
     * @return Can return -1 if error occurred
     */
    public int getVoteCount(ProxiedPlayer p) {
        if(isReady()) {
            try {
                Statement stmt = mySQL.createStatement();
                String sql = "SELECT votecount FROM vote_count WHERE uuid = '%s'";
                sql = String.format(sql, getUUID(p.getUniqueId()));
                ResultSet rs = stmt.executeQuery(sql);

                int count = 0;
                while (rs.next()) {
                    count = count + rs.getInt("votecount");
                }
                rs.close();
                stmt.close();
                return count;
            } catch(Exception e) {
                e.printStackTrace();
            }
        } else {
            return -1;
        }
        return -1;
    }

    public VoteObject getVotes(ProxiedPlayer p) {
        if(isReady()) {
            try {
                VoteObject obj = null;
                String sql = "SELECT * FROM vote_count WHERE uuid = '%s'";
                String sql2 = "SELECT valuecount FROM vote_points WHERE uuid = '%s'";
                sql = String.format(sql, getUUID(p.getUniqueId()));
                sql2 = String.format(sql2, getUUID(p.getUniqueId()));
                Statement stmt = mySQL.createStatement();
                Statement stmt2 = mySQL.createStatement();

                ResultSet pset = stmt.executeQuery(sql2);
                int points = 0;
                while (pset.next()) {
                    points = pset.getInt("valuecount");
                }
                pset.close();
                stmt.close();

                ResultSet rs = stmt2.executeQuery(sql);
                while (rs.next()) {
                    obj = new VoteObject(
                            getUUID(p.getUniqueId()),
                            rs.getInt("votecount"),
                            points, rs.getDate("lastvote")
                    );
                }
                rs.close();
                stmt2.close();
                return obj;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }


    /**
     * Builds Advert with vote-information
     * @param p Player
     * @return TextComponent with Message for Player
     */
    public static TextComponent getVoteMessage(ProxiedPlayer p) {
        VoteObject vote = Vote.getInstance().getVotes(p);
        try {
            // Heute schon gevoted?
            TextComponent message1 = new TextComponent("Heute schon " + ChatColor.GREEN + "gevoted?\n");

            // Wenn du regelmäßig votest unterstützt du nicht nur den Server, sondern bekommst auch tolle Belohnungen
            TextComponent message2 = new TextComponent(ChatColor.RESET + "Wenn du regelmäßig votest, " +
                    ChatColor.RED + ChatColor.BOLD + "unterstützt " + ChatColor.RESET +
                    "du den Server nicht nur, sondern bekommst auch " +
                    ChatColor.RED + ChatColor.BOLD + "tolle Belohnungen!\n");

            TextComponent message3;
            if (vote == null) {
                // Du hast noch nicht gevoted
                message3 = new TextComponent(ChatColor.RESET + "Du hast noch nicht gevoted\n");
            } else {
                // Du hast bereits X mal gevoted
                message3 = new TextComponent(ChatColor.RESET + "Du hast bereits " +
                        ChatColor.GOLD + ChatColor.BOLD + vote.getVotes() + ChatColor.RESET + " mal gevoted" +
                        "und hast aktuell " + ChatColor.BOLD + ChatColor.GOLD + vote.getCoins() + ChatColor.RESET + " Votecoins!");
            }

            // Zum Voten hier klicken: VOTE
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
