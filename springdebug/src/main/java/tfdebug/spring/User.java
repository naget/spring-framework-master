package tfdebug.spring;

/**
 * Created by tianfeng on 2018/4/30.
 */
public class User {
    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    private String userName;
    private String email;
}
