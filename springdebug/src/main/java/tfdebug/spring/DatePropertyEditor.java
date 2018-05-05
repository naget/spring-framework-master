package tfdebug.spring;

import java.beans.PropertyEditorSupport;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
//自定义的属性编辑器，解析UserManager中的Date属性
public class DatePropertyEditor extends PropertyEditorSupport {
    private String format = "yyyy-MM-dd";

    public void setFormat(String format) {
        this.format = format;
    }
    public void setAsText(String arg0) throws IllegalArgumentException{
        System.out.println("arg0:"+arg0);
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(format);
        try{
            Date d =simpleDateFormat.parse(arg0);
            this.setValue(d);
        }catch (ParseException e){
            e.printStackTrace();
        }

    }
}
