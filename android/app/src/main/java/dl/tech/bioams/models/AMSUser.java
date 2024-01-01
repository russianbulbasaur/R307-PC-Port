package dl.tech.bioams.models;

public class AMSUser {
    public String userid;
    public String name;
    public int fingerprint;
    public AMSUser(String uid,String name,int fp){
        this.fingerprint = fp;
        this.userid = uid;
        this.name = name;
    }
}
