package nz.ac.auckland.stubble;

import nz.ac.auckland.stubble.stub.StubDefinition;
import org.apache.camel.CamelContext;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;

import java.util.ArrayList;
import java.util.List;

public abstract class Stubble {

    private List<StubDefinition.StubDefinitionBuilderInit> stubBuilders = new ArrayList<>();

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

    //add spring support

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
        stub("...").responseBody(...)

        stub("...")....
    }
  }.run()
*/