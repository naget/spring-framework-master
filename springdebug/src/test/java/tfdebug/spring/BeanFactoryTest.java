package tfdebug.spring;

import org.junit.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.core.io.ClassPathResource;

import static org.junit.Assert.assertEquals;

/**
 * Created by tianfeng on 2018/4/28.
 */
@SuppressWarnings("deprecation")
public class BeanFactoryTest {
    @Test
    public void testSimpleLoad(){
        BeanFactory beanFactory = new XmlBeanFactory(new ClassPathResource("spring-beans"));
        MyTestBean myTestBean = (MyTestBean) beanFactory.getBean("myTestBean");
        assertEquals("teststr",myTestBean.getTestStr());

    }
    @Test
    public void testCustomPraser(){
        ApplicationContext bf = new ClassPathXmlApplicationContext("spring-beans");

        User user = (User) bf.getBean("testbean");
        System.out.println(user.getUserName()+","+user.getEmail());
    }
    @Test
    public void testPropertyEditor(){
        ApplicationContext bf = new ClassPathXmlApplicationContext("spring-beans");

        UserManager user = (UserManager) bf.getBean("userManager");
        System.out.println(user.getDateValue());
    }
    @Test
    public void useBeanFactoryPostProcessor(){
        ConfigurableListableBeanFactory bf = new XmlBeanFactory(new ClassPathResource("spring-beans"));
        BeanFactoryPostProcessor bfpp = (BeanFactoryPostProcessor) bf.getBean("bfpp");
        bfpp.postProcessBeanFactory(bf);
        System.out.println(bf.getBean("simpleBean"));
    }
}
