package net.snapecraft.vote;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class VoteObject {
    private String uuid;
    private int votes;
    private int coins;
    private Date last;

    public VoteObject(String uuid, int votes, int coins, String lastvote) {
        this.uuid = uuid;
        this.votes = votes;
        this.coins = coins;
        DateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        try {
            this.last = format.parse(lastvote);
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }
    public VoteObject(String uuid, int votes, int coins, Date lastvote) {
        this.uuid = uuid;
        this.votes = votes;
        this.coins = coins;
        this.last = lastvote;
    }
    public Date getLast() {
        return last;
    }
    public int getVotes() {
        return votes;
    }
    public String getUuid() {
        return uuid;
    }
    public int getCoins() {
        return coins;
    }
}
