package nz.ac.auckland.stubble;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import nz.ac.auckland.morc.MorcMethods;
import nz.ac.auckland.morc.endpointoverride.EndpointOverride;
import nz.ac.auckland.stubble.stub.StubDefinition;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.language.ConstantExpression;
import org.apache.camel.spring.SpringCamelContext;
import org.custommonkey.xmlunit.XMLUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.*;

public abstract class Stubble implements MorcMethods {

    private static final Logger logger = LoggerFactory.getLogger(Stubble.class);
    private List<StubDefinition.StubDefinitionBuilderInit> stubBuilders = new ArrayList<>();
    private String[] springContextPaths = new String[]{};
    private String propertiesLocationPath;

    protected abstract void configure();

    protected StubDefinition.StubDefinitionBuilder stub(String description, String uri) {
        if (uri.startsWith("http")) uri = "jetty:" + uri;
        StubDefinition.StubDefinitionBuilder builder = new StubDefinition.StubDefinitionBuilder(description, uri);
        stubBuilders.add(builder);
        return builder;

    }

    protected StubDefinition.StubDefinitionBuilder stub(String uri) {
        int stubCount = stubBuilders.size();
        return stub("Stub " + stubCount,uri);
    }

    /**
     * Override this to return a list of Spring context paths on the classpath
     *
     * @return An array of classpath Spring XML file references
     */
    protected String[] getSpringContextPaths() {
        return springContextPaths;
    }

    /**
     * Override this to return a path to a properties file for managing Camel endpoint URIs
     *
     * @return A string path to a properties file
     */
    protected String getPropertiesLocation() {
        return propertiesLocationPath;
    }

    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(getSpringContextPaths());
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = SpringCamelContext.springCamelContext(createApplicationContext(), false);

        String propertiesLocation = getPropertiesLocation();
        if (propertiesLocation != null) {
            PropertiesComponent properties = new PropertiesComponent();
            properties.setLocation(propertiesLocation);
            context.addComponent("properties", properties);
        }

        return context;
    }

    /**
     * Configure XML Unit parameters for comparing XML - override this to adjust the defaults
     */
    protected void configureXmlUnit() {
        XMLUnit.setIgnoreWhitespace(true);
        XMLUnit.setNormalizeWhitespace(true);
        XMLUnit.setIgnoreComments(true);
    }

    protected void configureLogging() {
        JoranConfigurator configurator = new JoranConfigurator();
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        try {
            configurator.setContext(context);
            context.reset();
            configurator.doConfigure(classpath("/logback-stubble.xml"));
        } catch (JoranException e) {
            throw new RuntimeException(e);
        }
    }

    public void run() throws Exception {
        configureLogging();
        configureXmlUnit();
        CamelContext context = createCamelContext();

        configure();

        stubBuilders.stream().forEach(b -> {
            try {
                context.addRoutes(
                        new RouteBuilder() {
                            @Override
                            public void configure() throws Exception {
                                StubDefinition stub = b.build();

                                logger.info("Starting stub {} for endpoint {}", stub.getDescription(),stub.getEndpointUri());

                                Endpoint targetEndpoint = context.getEndpoint(stub.getEndpointUri());
                                for (EndpointOverride override : stub.getEndpointOverrides())
                                    override.overrideEndpoint(targetEndpoint);

                                RouteDefinition routeDefinition = new RouteDefinition();

                                routeDefinition.from(stub.getEndpointUri())
                                        .convertBodyTo(byte[].class)
                                        .routeId(Stubble.class.getCanonicalName() + "." + stub.getDescription().replaceAll("\\s+", ""))
                                        .setProperty("endpointUri", new ConstantExpression(stub.getEndpointUri()))
                                        .log(LoggingLevel.DEBUG, "Endpoint ${property.endpointUri} received body: ${body}, headers: ${headers}");

                                if (stub.getStubFeedPreprocessor() != null)
                                    routeDefinition.process(stub.getStubFeedPreprocessor());

                                routeDefinition.process(stub.getSelectorProcessor())
                                        .log(LoggingLevel.DEBUG, "Endpoint ${property.endpointUri} returning back to the client body: ${body}, headers: ${headers}");

                                context.addRouteDefinition(routeDefinition);
                            }
                        });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                context.stop();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }));

        try {
            context.start();
            synchronized (this) {
                this.wait();
            }
            context.stop();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}