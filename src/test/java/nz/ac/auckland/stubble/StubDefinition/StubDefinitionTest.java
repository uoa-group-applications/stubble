package nz.ac.auckland.stubble.StubDefinition;

import nz.ac.auckland.morc.MorcMethods;
import nz.ac.auckland.morc.endpointoverride.CxfEndpointOverride;
import nz.ac.auckland.morc.endpointoverride.EndpointOverride;
import nz.ac.auckland.morc.endpointoverride.UrlConnectionOverride;
import nz.ac.auckland.stubble.stub.StubDefinition;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.junit.Assert;
import org.junit.Test;

public class StubDefinitionTest extends Assert implements MorcMethods {

    @Test
    public void testSimpleStub() throws Exception {
        StubDefinition stub = new StubDefinition.StubDefinitionBuilder("foo", "foo.com")
                .response(text("foo"))
                .addRepeatedProcessor(headers(header("12", "34")))
                .response(text("baz"), headers(header("foo", "baz")))
                .addProcessors(text("moo"))
                .addProcessors(2, headers(header("1", "2"))).build();

        assertEquals("foo", stub.getDescription());

        Exchange e = new DefaultExchange(new DefaultCamelContext());

        stub.getSelectorProcessor().process(e);
        assertEquals("foo", e.getIn().getBody(String.class));
        assertEquals("34", e.getIn().getHeader("12"));

        stub.getSelectorProcessor().process(e);
        assertEquals("baz", e.getIn().getBody(String.class));
        assertEquals("baz", e.getIn().getHeader("foo"));
        assertEquals("34", e.getIn().getHeader("12"));

        stub.getSelectorProcessor().process(e);
        assertEquals("moo", e.getIn().getBody(String.class));
        assertEquals("2", e.getIn().getHeader("1"));
        assertEquals("34", e.getIn().getHeader("12"));

        stub.getSelectorProcessor().process(e);
        assertEquals("foo", e.getIn().getBody(String.class));
        assertEquals("34", e.getIn().getHeader("12"));
    }

    @Test
    public void testMatchedResponses() throws Exception {
        StubDefinition stub = new StubDefinition.StubDefinitionBuilder("foo", "foo.com")
                .preprocessor(new Processor() {
                    int count = 0;

                    @Override
                    public void process(Exchange exchange) throws Exception {
                        exchange.getIn().setHeader("preprocessed", count++);
                    }
                })
                .matchedResponses(defaultResponse(text("123")),
                        response(text("foo"), xml("<foo/>")),
                        response(text("baz"), xml("<baz/>"))).build();

        Exchange e = new DefaultExchange(new DefaultCamelContext());

        e.getIn().setBody("baz");
        stub.getSelectorProcessor().process(e);
        stub.getStubFeedPreprocessor().process(e);
        assertTrue(xml("<baz/>").matches(e));
        assertEquals(0, e.getIn().getHeader("preprocessed"));

        e.getIn().setBody("foo");
        stub.getSelectorProcessor().process(e);
        stub.getStubFeedPreprocessor().process(e);
        assertTrue(xml("<foo/>").matches(e));
        assertEquals(1, e.getIn().getHeader("preprocessed"));

        e.getIn().setBody("something");
        stub.getSelectorProcessor().process(e);
        stub.getStubFeedPreprocessor().process(e);
        assertTrue(text("123").matches(e));
        assertEquals(2, e.getIn().getHeader("preprocessed"));

        e.getIn().setBody("baz");
        stub.getSelectorProcessor().process(e);
        stub.getStubFeedPreprocessor().process(e);
        assertTrue(xml("<baz/>").matches(e));
        assertEquals(3, e.getIn().getHeader("preprocessed"));
    }

    public void testMatchedResponsesNoDefault() throws Exception {
        StubDefinition stub = new StubDefinition.StubDefinitionBuilder("foo", "foo.com")
                .matchedResponses(response(text("foo"), xml("<foo/>")),
                        response(text("baz"), xml("<baz/>"))).build();

        Exchange e = new DefaultExchange(new DefaultCamelContext());

        e.getIn().setBody("baz");
        stub.getSelectorProcessor().process(e);
        assertTrue(xml("<baz/>").matches(e));

        e.getIn().setBody("foo");
        stub.getSelectorProcessor().process(e);
        assertTrue(xml("<foo/>").matches(e));

        e.getIn().setBody("something");
        stub.getSelectorProcessor().process(e);
        assertTrue(text("something").matches(e));
    }

    @Test
    public void testCustomSelector() throws Exception {
        StubDefinition stub = new StubDefinition.StubDefinitionBuilder("foo", "foo.com")
                .response(text("foo"))
                .response(text("baz"))
                .response(text("moo"))
                .selector(randomSelector()).build();

        Exchange e = new DefaultExchange(new DefaultCamelContext());

        int foo = 0, baz = 0, moo = 0;

        for (int i = 0; i < 1000; i++) {
            stub.getSelectorProcessor().process(e);
            if (e.getIn().getBody().equals("foo")) foo++;
            else if (e.getIn().getBody().equals("baz")) baz++;
            else if (e.getIn().getBody().equals("moo")) moo++;
            else throw new RuntimeException("unknown body");
        }

        assertTrue(foo > 0);
        assertTrue(baz > 0);
        assertTrue(moo > 0);
    }

    @Test
    public void testEndpointOverride() throws Exception {
        StubDefinition stub = new StubDefinition.StubDefinitionBuilder("foo", "foo.com")
                .response(text("foo"))
                .addEndpointOverride(endpoint -> {
                }).build();

        assertEquals(3, stub.getEndpointOverrides().size());

        boolean cxfOverride = false;
        boolean connectionOverride = false;

        for (EndpointOverride override : stub.getEndpointOverrides()) {
            if (override instanceof CxfEndpointOverride) cxfOverride = true;
            if (override instanceof UrlConnectionOverride) connectionOverride = true;
        }

        assertTrue(cxfOverride);
        assertTrue(connectionOverride);
    }
}
