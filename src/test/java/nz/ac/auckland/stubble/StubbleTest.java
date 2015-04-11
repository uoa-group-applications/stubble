package nz.ac.auckland.stubble;

import nz.ac.auckland.morc.MorcTestBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.junit.Assert;
import org.junit.Test;

public class StubbleTest extends Assert {

    @Test
    public void testOneStub() throws Exception {
        new Thread() {
            public void run() {
                try {
                    new Stubble() {
                        @Override
                        protected void configure() {
                            stub("http://localhost:8080")
                                    .response(text("foo"))
                                    .response(text("baz"));
                        }
                    }.run();
                } catch(Exception e) {
                    throw new RuntimeException(e);
                }
            }}.start();

        //wait for stubble to start (todo: make this better)
        Thread.sleep(5000);

        assertEquals(0,new MorcTestBuilder() {
            public void configure() {
                syncTest("ping stubs","http://localhost:8080")
                        .request(text("a"))
                        .request(text("b"))
                        .request(text("c"))
                        .expectation(text("foo"))
                        .expectation(text("baz"))
                        .expectation(text("foo"));
            }
        }.run());
    }

    @Test
    public void testMultipleStubs() throws Exception {
        new Thread() {
            public void run() {
                try {
                    new Stubble() {
                        @Override
                        protected void configure() {
                            stub("http://localhost:8080")
                                    .response(text("foo"))
                                    .response(text("baz"));

                            stub("http://localhost:8081")
                                    .response(xml("<foo/>"))
                                    .response(xml("<baz/>"));

                            stub("http://localhost:8082")
                                    .response(json("{ \"foo\":\"baz\" }"))
                                    .response(json("{ \"baz\":\"foo\" }"));
                        }
                    }.run();
                } catch(Exception e) {
                    throw new RuntimeException(e);
                }
            }}.start();

        //wait for stubble to start (todo: make this better)
        Thread.sleep(5000);

        assertEquals(0,new MorcTestBuilder() {
            public void configure() {
                syncTest("ping stubs","http://localhost:8080")
                        .request(text("a"))
                        .request(text("b"))
                        .request(text("c"))
                        .expectation(text("foo"))
                        .expectation(text("baz"))
                        .expectation(text("foo"));

                syncTest("ping stubs","http://localhost:8081")
                        .request(text("a"))
                        .request(text("b"))
                        .request(text("c"))
                        .expectation(xml("<foo/>"))
                        .expectation(xml("<baz/>"))
                        .expectation(xml("<foo/>"));

                syncTest("ping stubs","http://localhost:8082")
                        .request(text("a"))
                        .request(text("b"))
                        .request(text("c"))
                        .expectation(json("{ \"foo\":\"baz\" }"))
                        .expectation(json("{ \"baz\":\"foo\" }"))
                        .expectation(json("{ \"foo\":\"baz\" }"));
            }
        }.run());
    }

    @Test
    public void testStubPreprocessor() throws Exception {
        new Thread() {
            public void run() {
                try {
                    new Stubble() {
                        @Override
                        protected void configure() {
                            stub("http://localhost:8080")
                                    .preprocessor(new Processor() {

                                        private int count = 0;

                                        @Override
                                        public void process(Exchange exchange) throws Exception {
                                            if (count++ % 2 == 0) exchange.getIn().setHeader("a","b");
                                        }
                                    })
                                    .response(text("foo"))
                                    .response(text("baz"));
                        }
                    }.run();
                } catch(Exception e) {
                    throw new RuntimeException(e);
                }
            }}.start();

        //wait for stubble to start (todo: make this better)
        Thread.sleep(5000);

        assertEquals(0,new MorcTestBuilder() {
            public void configure() {
                syncTest("ping stubs","http://localhost:8080")
                        .request(text("a"))
                        .request(text("b"))
                        .request(text("c"))
                        .expectation(headers(header("a", "b")), text("foo"))
                        .expectation(text("baz"))
                        .expectation(headers(header("a","b")),text("foo"));
            }
        }.run());
    }

    @Test
    public void testSpringStub() throws Exception {

    }

    @Test
    public void testProperties() throws Exception {

    }

}
