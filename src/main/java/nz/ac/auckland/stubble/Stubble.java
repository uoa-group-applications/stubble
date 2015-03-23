package nz.ac.auckland.stubble;

import nz.ac.auckland.morc.MorcMethods;
import nz.ac.auckland.stubble.stub.StubDefinition;
import org.apache.camel.CamelContext;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.custommonkey.xmlunit.XMLUnit;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.ArrayList;
import java.util.List;

public abstract class Stubble implements MorcMethods {

    private List<StubDefinition.StubDefinitionBuilderInit> stubBuilders = new ArrayList<>();
    private String[] springContextPaths = new String[]{};
    private String propertiesLocationPath;

    protected AbstractApplicationContext applicationContext;
    protected abstract AbstractApplicationContext createApplicationContext();

    public Stubble() {
        configureXmlUnit();
    }

    protected abstract void configure();

    //need support for Spring, properties

    protected StubDefinition.StubDefinitionBuilder stub(String description, String uri) {
        StubDefinition.StubDefinitionBuilder builder = new StubDefinition.StubDefinitionBuilder(description, uri);
        stubBuilders.add(builder);
        return builder;
    }

    protected StubDefinition.StubDefinitionBuilder stub(String description, String uri, Processor... processors) {
        StubDefinition.StubDefinitionBuilder builder = stub(description, uri);
        builder.addProcessors(processors);
        return builder;
    }

    /**
     * Override this to return a list of Spring context paths on the classpath
     *
     * @return An array of classpath Spring XML file references
     */
    public String[] getSpringContextPaths() {
        return springContextPaths;
    }

    /**
     * Override this to return a path to a properties file for managing Camel endpoint URIs
     *
     * @return A string path to a properties file
     */
    public String getPropertiesLocation() {
        return propertiesLocationPath;
    }

    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(getSpringContextPaths());
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

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

    public void run() throws Exception {
        CamelContext context = new DefaultCamelContext();

        stubBuilders.stream().forEach(b -> {
            try {
                context.addRoutes(
                        new RouteBuilder() {
                            @Override
                            public void configure() throws Exception {
                                StubDefinition stub = b.build();
                                from(stub.getEndpointUri())
                                        .routeId(stub.getDescription())
                                        .process(stub.getStubFeedPreprocessor())
                                        .process(stub.getSelectorProcessor());
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
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {

    }

}




/*
  new Stubble() {
    public void configure() {
        stub("http:/adsffsad").response(xml(adsfsfdafsd), headers...).response(body, headers...)
                   .responseBody().responseHeaders
        stub("...")....
    }
  }.run()
*/