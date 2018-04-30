package tfdebug.spring;

import org.springframework.beans.factory.xml.NamespaceHandlerSupport;

/**
 * Created by tianfeng on 2018/4/30.
 * 仅完成一个功能，将组件注册到spring容器
 */
public class MyNamespaceHandler extends NamespaceHandlerSupport {
    @Override
    public void init() {
        registerBeanDefinitionParser("user",new UserBeanDefinitionParser());
    }
}
