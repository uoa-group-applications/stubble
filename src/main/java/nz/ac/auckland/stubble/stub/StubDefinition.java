package nz.ac.auckland.stubble.stub;

import nz.ac.auckland.morc.MorcBuilder;
import nz.ac.auckland.morc.endpointoverride.CxfEndpointOverride;
import nz.ac.auckland.morc.endpointoverride.EndpointOverride;
import nz.ac.auckland.morc.endpointoverride.UrlConnectionOverride;
import nz.ac.auckland.morc.processor.*;
import nz.ac.auckland.morc.resource.HeadersTestResource;
import nz.ac.auckland.morc.resource.TestResource;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.search.HeaderTerm;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.util.*;

/**
 * A general class for declaring stubs
 *
 * @author David MacDonald <d.macdonald@auckland.ac.nz>
 */
public class StubDefinition {
    private static final Logger logger = LoggerFactory.getLogger(StubDefinition.class);

    private String description;
    private String endpointUri;
    private SelectorProcessor selectorProcessor;
    private Processor stubFeedPreprocessor;
    private Collection<EndpointOverride> endpointOverrides = new ArrayList<>();

    public String getDescription() {
        return description;
    }

    public String getEndpointUri() {
        return endpointUri;
    }

    public SelectorProcessor getSelectorProcessor() {
        return selectorProcessor;
    }

    public Processor getStubFeedPreprocessor() {
        return stubFeedPreprocessor;
    }

    public Collection<EndpointOverride> getEndpointOverrides() {
        return Collections.unmodifiableCollection(endpointOverrides);
    }

    /**
     * A concrete implementation of StubDefinitionBuilderInit
     */
    public static class StubDefinitionBuilder extends StubDefinitionBuilderInit<StubDefinitionBuilder> {
        public StubDefinitionBuilder(String description, String endpointUri) {
            super(description, endpointUri);
        }
    }

    public static class StubDefinitionBuilderInit<Builder extends StubDefinitionBuilderInit<Builder>> {

        private String description;
        private String endpointUri;
        private List<List<Processor>> processors = new ArrayList<>();
        private List<Processor> repeatedProcessors = new ArrayList<>();
        private Class<? extends SelectorProcessor> selectorProcessorClass = SelectorProcessor.class;
        private SelectorProcessor selectorProcessor;
        private Processor stubFeedPreprocessor;
        private Collection<EndpointOverride> endpointOverrides = new ArrayList<>();

        /**
         * @param endpointUri A Camel Endpoint URI to listen to for expected messages
         */
        public StubDefinitionBuilderInit(String description, String endpointUri) {
            try {
                this.endpointUri = URISupport.normalizeUri(endpointUri);
            } catch (URISyntaxException | UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }

            this.description = description;

            //we don't want to use POJO to receive messages
            endpointOverrides.add(new CxfEndpointOverride());
            endpointOverrides.add(new UrlConnectionOverride());
        }

        /**
         * @param selectorProcessorClass    A class for selecting which of the processors to use for handling a response.
         *                                  The default implementation loops through the processors
         */
        public Builder selector(Class<? extends SelectorProcessor> selectorProcessorClass) {
            this.selectorProcessorClass = selectorProcessorClass;
            return self();
        }

        /**
         * Adds processors for populating a series of exchanges with an outgoing message - all processors in a single call
         * apply ONLY to a single message, add consecutive calls to addProcessors in order to handle further messages
         *
         * @param processors A list of processors that will handle a separate exchange (in order)
         */
        public Builder addProcessors(Processor... processors) {
            this.processors.add(new ArrayList<>(Arrays.asList(processors)));
            return self();
        }

        /**
         * Add a set of processors to handle an outgoing exchange at a particular offset (n'th message)
         *
         * @param index      The exchange offset that these processors should be applied to
         * @param processors The processors that will handle populating the exchange with an appropriate outgoing value
         */
        public Builder addProcessors(int index, Processor... processors) {
            while (index >= this.processors.size()) {
                this.processors.add(new ArrayList<Processor>());
            }
            this.processors.get(index).addAll(new ArrayList<>(Arrays.asList(processors)));
            return self();
        }

        @SuppressWarnings("unchecked")
        public Builder response(Processor... processors) {
            return addProcessors(processors);
        }

        public Builder matchedResponses(MatchedResponseProcessor.MatchedResponse... responses) {
            return addProcessors(new MatchedResponseProcessor(responses));
        }

        /**
         * @param processor A processor that will be applied to every outgoing message
         */
        public Builder addRepeatedProcessor(Processor processor) {
            repeatedProcessors.add(processor);
            return self();
        }

        /**
         * @return A list of processors that will be used to handle each exchange; note that a single Processor is returned
         *         that effectively wraps all of the processors provided to the builder (including repeated processors)
         */
        protected List<Processor> getProcessors() {
            List<Processor> finalProcessors = new ArrayList<>();

            for (List<Processor> localProcessors : processors) {
                List<Processor> orderedProcessors = new ArrayList<>(localProcessors);
                orderedProcessors.addAll(0, repeatedProcessors);
                finalProcessors.add(new MultiProcessor(orderedProcessors));
            }

            return finalProcessors;
        }

        /**
         * @return A processor that will be applied before the exchange is sent through to the mock endpoint
         */
        public Processor getStubFeedPreprocessor() {
            return stubFeedPreprocessor;
        }

        /**
         * @param stubFeedPreprocessor A processor that will be applied before the exchange is sent through to the mock endpoint
         */
        public Builder stubFeedPreprocessor(Processor stubFeedPreprocessor) {
            this.stubFeedPreprocessor = stubFeedPreprocessor;
            return self();
        }

        /**
         * @param override An override used for modifying an endpoint with sensible properties
         */
        public Builder addEndpointOverride(EndpointOverride override) {
            //skip the ones we're already aware of
            if (override instanceof CxfEndpointOverride || override instanceof UrlConnectionOverride) return self();
            endpointOverrides.add(override);
            return self();
        }

        /**
         * @return The endpoint overrides that will be used to modify endpoint properties
         */
        public Collection<EndpointOverride> getEndpointOverrides() {
            return Collections.unmodifiableCollection(this.endpointOverrides);
        }

        /**
         * @return The endpoint URI that this definition expects to act against
         */
        public String getEndpointUri() {
            return this.endpointUri;
        }

        public StubDefinition build() {
            try {
                selectorProcessor = selectorProcessorClass.getDeclaredConstructor(List.class)
                        .newInstance(Collections.unmodifiableList(getProcessors()));
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException
                    | NoSuchMethodException e) {
                throw new RuntimeException(e);
            }

            return new StubDefinition(this);
        }

        @SuppressWarnings("unchecked")
        protected Builder self() {
            return (Builder) this;
        }

    }

    @SuppressWarnings("unchecked")
    private StubDefinition(StubDefinitionBuilderInit builder) {
        this.endpointUri = builder.getEndpointUri();
        this.endpointOverrides = builder.getEndpointOverrides();
        this.selectorProcessor = builder.selectorProcessor;
        this.description = builder.description;
        this.stubFeedPreprocessor = builder.stubFeedPreprocessor;
    }
}
