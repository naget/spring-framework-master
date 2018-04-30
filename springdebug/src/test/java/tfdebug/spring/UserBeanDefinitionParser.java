package tfdebug.spring;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser;
import org.springframework.util.StringUtils;
import org.w3c.dom.Element;

/**
 * Created by tianfeng on 2018/4/30.
 * 用来解析xsd文件中的定义和组定义
 */
public class UserBeanDefinitionParser extends AbstractSingleBeanDefinitionParser{
    protected Class getBeanClass(Element element){
        return User.class;
    }
    protected void doParse(Element element, BeanDefinitionBuilder bean){
        String userName= element.getAttribute("userName");
        String email = element.getAttribute("email");
        //将数据放入BeanDefinitionBuilder中，待完成所有bean的解析后统一注册到beanfactory中
        if (StringUtils.hasText(userName)){
            bean.addPropertyValue("userName",userName);
        }
        if (StringUtils.hasText(email)){
            bean.addPropertyValue("email",email);
        }
    }
}
