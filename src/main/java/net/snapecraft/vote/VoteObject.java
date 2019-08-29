package net.snapecraft.vote;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class VoteObject {
    String uuid;
    int votes;
    Date last;

    public VoteObject(String uuid, int votes, String lastvote) {
        this.uuid = uuid;
        this.votes = votes;
        DateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        try {
            this.last = format.parse(lastvote);
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }
    public VoteObject(String uuid, int votes, Date lastvote) {
        this.uuid = uuid;
        this.votes = votes;
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
}
