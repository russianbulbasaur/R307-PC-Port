package dl.tech.bioams.models;

import java.io.Serializable;

public class User implements Serializable {
    public String token;
    public String baseUrl;
    public String name;
    public String email;
    public String password;
    public User(String parsedToken,String parsedName,String baseUrl,String parsedEmail,String parsedPassword){
        this.token = parsedToken;
        this.name = parsedName;
        this.baseUrl = baseUrl;
        this.email = parsedEmail;
        this.password = parsedPassword;
    }
}
