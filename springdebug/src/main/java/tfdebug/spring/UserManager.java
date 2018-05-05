package tfdebug.spring;

import java.util.Date;

public class UserManager {
    private Date dateValue;

    public Date getDateValue() {
        return dateValue;
    }

    public void setDateValue(Date dateValue) {
        this.dateValue = dateValue;
    }

    @Override
    public String toString() {
        return "UserManager{" +
                "dateValue=" + dateValue +
                '}';
    }
}
