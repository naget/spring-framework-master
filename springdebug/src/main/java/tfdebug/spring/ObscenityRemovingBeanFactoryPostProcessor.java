package tfdebug.spring;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionVisitor;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.util.StringValueResolver;

import java.util.HashSet;
import java.util.Set;
//自定义的beanFactoryPostProcessor，实现的功能就是如果一个bean中出现了set中的字符串，就替换成******
public class ObscenityRemovingBeanFactoryPostProcessor implements BeanFactoryPostProcessor {
    private Set<String> obcenties;
    public ObscenityRemovingBeanFactoryPostProcessor(){
        this.obcenties = new HashSet<String>();
    }
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        String[] beanNames = beanFactory.getBeanDefinitionNames();
        for (String beanName:beanNames){
            BeanDefinition bd = beanFactory.getBeanDefinition(beanName);
            StringValueResolver valueResolver = new StringValueResolver() {
                @Override
                public String resolveStringValue(String strVal) {
                    if (isObscene(strVal))return "*****";
                    return strVal;
                }
            };
            BeanDefinitionVisitor visitor = new BeanDefinitionVisitor(valueResolver);
            visitor.visitBeanDefinition(bd);
         }
    }

    public boolean isObscene(Object value){
        String potentialObscenity = value.toString().toUpperCase();
        return this.obcenties.contains(potentialObscenity);
    }

    public void setObscenties(Set<String> obscenties){
        this.obcenties.clear();
        for (String obscenity:obscenties){
            this.obcenties.add(obscenity.toUpperCase());
        }
    }
}
