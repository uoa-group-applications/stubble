package nz.ac.auckland.stubble

new Stubble() {
    public void configure() {
        stub("http://0.0.0.0:8080")
                .response(xml("<foo/>"))
                .response(json("{ \"foo\" : \"baz\" }"));
    }
}.run();