package com.github.ulisesbocchio.spring.boot.security.saml.configuration;

import com.github.ulisesbocchio.spring.boot.security.saml.configurer.*;
import com.github.ulisesbocchio.spring.boot.security.saml.properties.SAMLSSOProperties;
import lombok.extern.slf4j.Slf4j;
import org.opensaml.xml.parse.ParserPool;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.saml.SAMLAuthenticationProvider;
import org.springframework.security.saml.context.SAMLContextProvider;
import org.springframework.security.saml.key.KeyManager;
import org.springframework.security.saml.metadata.ExtendedMetadata;
import org.springframework.security.saml.metadata.MetadataManager;
import org.springframework.security.saml.parser.ParserPoolHolder;
import org.springframework.security.saml.processor.SAMLProcessor;
import org.springframework.security.saml.websso.*;

import java.util.*;

import static com.github.ulisesbocchio.spring.boot.security.saml.util.FunctionalUtils.unchecked;

/**
 * Spring Security configuration entry point for the Service Provider. This configuration class basically collects
 * all relevant beans present in the application context to initialize and configure all {@link ServiceProviderConfigurer}
 * present in the context. Usually one {@link ServiceProviderConfigurer} is enough and preferably one that extends
 * {@link ServiceProviderConfigurerAdapter} which provides empty implementations and subclasses can implement only the
 * relevant method(s) for the purpose of the current application.
 * <p>
 * All {@code required=false} autowired beans can be provided as beans by the user instead of using the
 * {@link ServiceProviderConfigurer} DSL.
 *
 * @author Ulises Bocchio
 */
@Configuration
@Order(-20)
public class SAMLServiceProviderSecurityConfiguration extends WebSecurityConfigurerAdapter {

    private List<ServiceProviderConfigurer> serviceProviderConfigurers = Collections.emptyList();

    @Autowired
    private ObjectPostProcessor<Object> objectPostProcessor;

    @Autowired
    private SAMLSSOProperties sAMLSsoProperties;

    @Autowired
    private ResourceLoader resourceLoader;

    @Autowired
    DefaultListableBeanFactory beanFactory;

    @Autowired(required = false)
    private ExtendedMetadata extendedMetadata;

    @Autowired(required = false)
    SAMLContextProvider samlContextProvider;

    @Autowired(required = false)
    KeyManager keyManager;

    @Autowired(required = false)
    MetadataManager metadataManager;

    @Autowired(required = false)
    SAMLProcessor samlProcessor;

    @Autowired(required = false)
    @Qualifier("webSSOprofileConsumer")
    @SuppressWarnings("SpringJavaAutowiringInspection")
    private WebSSOProfileConsumer webSSOProfileConsumer;

    @Autowired(required = false)
    @Qualifier("hokWebSSOprofileConsumer")
    @SuppressWarnings("SpringJavaAutowiringInspection")
    WebSSOProfileConsumerHoKImpl hokWebSSOProfileConsumer;

    @Autowired(required = false)
    @Qualifier("webSSOprofile")
    @SuppressWarnings("SpringJavaAutowiringInspection")
    WebSSOProfile webSSOProfile;

    @Autowired(required = false)
    @Qualifier("ecpProfile")
    @SuppressWarnings("SpringJavaAutowiringInspection")
    WebSSOProfileECPImpl ecpProfile;

    @Autowired(required = false)
    @Qualifier("hokWebSSOProfile")
    @SuppressWarnings("SpringJavaAutowiringInspection")
    WebSSOProfileHoKImpl hokWebSSOProfile;

    @Autowired(required = false)
    SingleLogoutProfile sloProfile;

    @Autowired(required = false)
    SAMLAuthenticationProvider samlAuthenticationProvider;

    /**
     * Configures Spring Security as a SAML 2.0 Service provider using a {@link ServiceProviderSecurityBuilder}, user
     * provided {@link ServiceProviderConfigurer}s, and {@link ServiceProviderSecurityConfigurer}
     */
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        //All existing beans are thrown as shared objects to the ServiceProviderSecurityBuilder, which will wire all
        //beans/objects related to spring security SAML.
        ServiceProviderSecurityBuilder securityConfigurerBuilder = new ServiceProviderSecurityBuilder(objectPostProcessor, beanFactory, beanRegistry());
        securityConfigurerBuilder.setSharedObject(ParserPool.class, ParserPoolHolder.getPool());
        securityConfigurerBuilder.setSharedObject(WebSSOProfileConsumerImpl.class, (WebSSOProfileConsumerImpl) webSSOProfileConsumer);
        securityConfigurerBuilder.setSharedObject(WebSSOProfileConsumerHoKImpl.class, hokWebSSOProfileConsumer);
        securityConfigurerBuilder.setSharedObject(ServiceProviderEndpoints.class, new ServiceProviderEndpoints());
        securityConfigurerBuilder.setSharedObject(ResourceLoader.class, resourceLoader);
        securityConfigurerBuilder.setSharedObject(SAMLSSOProperties.class, sAMLSsoProperties);
        securityConfigurerBuilder.setSharedObject(ExtendedMetadata.class, extendedMetadata);
        securityConfigurerBuilder.setSharedObject(AuthenticationManager.class, authenticationManagerBean());
        securityConfigurerBuilder.setSharedObject(BeanRegistry.class, beanRegistry());
        securityConfigurerBuilder.setSharedObject(SAMLAuthenticationProvider.class, samlAuthenticationProvider);
        securityConfigurerBuilder.setSharedObject(SAMLContextProvider.class, samlContextProvider);
        securityConfigurerBuilder.setSharedObject(KeyManager.class, keyManager);
        securityConfigurerBuilder.setSharedObject(MetadataManager.class, metadataManager);
        securityConfigurerBuilder.setSharedObject(SAMLProcessor.class, samlProcessor);
        securityConfigurerBuilder.setSharedObject(WebSSOProfile.class, webSSOProfile);
        securityConfigurerBuilder.setSharedObject(WebSSOProfileECPImpl.class, ecpProfile);
        securityConfigurerBuilder.setSharedObject(WebSSOProfileHoKImpl.class, hokWebSSOProfile);
        securityConfigurerBuilder.setSharedObject(SingleLogoutProfile.class, sloProfile);
        securityConfigurerBuilder.setSharedObject(WebSSOProfileConsumer.class, webSSOProfileConsumer);
        securityConfigurerBuilder.setSharedObject(WebSSOProfileConsumerHoKImpl.class, hokWebSSOProfileConsumer);

        //To keep track of which beans were present in the Spring Context and which not, we register them in a
        //BeanRegistry bean. A custom inner type of this class.
        markBeansAsRegistered(securityConfigurerBuilder.getSharedObjects());

        //For each configurer found, we allow further customization of the HttpSecurity Object, and we expose the
        //ServiceProviderSecurityBuilder to the configurer of customization of the service provider.
        serviceProviderConfigurers.stream().forEach(unchecked(c -> c.configure(http)));
        serviceProviderConfigurers.stream().forEach(unchecked(c -> c.configure(securityConfigurerBuilder)));

        //After the builder has been customized by the configurer(s) provided, it's time to build the builder,
        //which builds a SecurityConfigurer that will wire spring security with the Service Provider configuration
        ServiceProviderSecurityConfigurer securityConfigurer = securityConfigurerBuilder.build();
        securityConfigurer.init(http);
        securityConfigurer.configure(http);
    }

    /**
     * For each object present in the map, register them in the bean registry.
     *
     * @param sharedObjects the objects to register.
     */
    private void markBeansAsRegistered(Map<Class<Object>, Object> sharedObjects) {
        sharedObjects.entrySet()
                .forEach(entry -> beanRegistry().addRegistered(entry.getKey(), entry.getValue()));
    }

    /**
     * Autowire {@link ServiceProviderConfigurer}s into the bean.
     *
     * @param serviceProviderConfigurers user provided {@link ServiceProviderConfigurer}s
     */
    @Autowired(required = false)
    public void setServiceProviderConfigurers(List<ServiceProviderConfigurer> serviceProviderConfigurers) {
        this.serviceProviderConfigurers = serviceProviderConfigurers;
    }

    /**
     * {@link BeanRegistry} bean registration. Used to store registered  and singleton beans, the latter
     * are created within the bounds of this plugin and some need to be exposed as beans.
     *
     * @return the {@link BeanRegistry}
     */
    @Bean
    public BeanRegistry beanRegistry() {
        return new BeanRegistry(beanFactory);
    }

    /**
     * Strategy for keeping track of registered and singleton beans. Registered are beans that have a Bean Definition in
     * the Spring Context. Singletons are those exposed by this plugin "manually", as a way to overcome certain design
     * flaws in Spring Security SAML. Particularly the fact that most classes have {@link Autowired} annotations, and as
     * they mostly implement {@link InitializingBean}, this plugin overcomes that problem by registering the internally
     * created objects as singletons with the {@link DefaultListableBeanFactory} so they can get autowired before the
     * {@link ObjectPostProcessor} provided by Spring Security calls the {@link InitializingBean#afterPropertiesSet()}
     * method on the beans. This way, the lifecycle of this beans is at most "semi-automatic", and this class provides
     * a destroy mechanism to dispose of those singleton beans that implement {@link DisposableBean}.
     */
    @Slf4j
    public static class BeanRegistry implements DisposableBean {
        private Map<String, Object> singletons = new HashMap<>();
        private Map<Class<?>, Object> registeredBeans = new HashMap<>();
        private DefaultListableBeanFactory beanFactory;

        public BeanRegistry(DefaultListableBeanFactory beanFactory) {
            this.beanFactory = beanFactory;
        }

        public void addSingleton(String name, Object bean) {
            Optional.ofNullable(bean)
                    .ifPresent(b -> singletons.put(name, bean));
        }

        public void addRegistered(Object bean) {
            addRegistered(bean.getClass(), bean);
        }

        public void addRegistered(Class<?> clazz, Object bean) {
            Optional.ofNullable(bean)
                    .ifPresent(b -> registeredBeans.put(clazz, bean));
        }

        public boolean isRegistered(Object bean) {
            return Optional.ofNullable(bean)
                    .map(Object::getClass)
                    .map(registeredBeans::containsKey)
                    .orElse(false);
        }

        public void destroy() throws Exception {
            singletons.keySet()
                    .stream()
                    .forEach(this::destroySingleton);
        }

        private void destroySingleton(String beanName) {
            log.debug("Destroying singleton: {}", beanName);
            beanFactory.destroySingleton(beanName);
        }
    }
}