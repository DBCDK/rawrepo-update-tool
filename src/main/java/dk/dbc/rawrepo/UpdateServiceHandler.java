package dk.dbc.rawrepo;

import dk.dbc.marc.binding.DataField;
import dk.dbc.marc.binding.MarcRecord;
import dk.dbc.marc.binding.SubField;
import dk.dbc.marc.reader.MarcReader;
import dk.dbc.marc.reader.MarcReaderException;
import dk.dbc.marc.writer.MarcXchangeV1Writer;
import dk.dbc.oss.ns.catalogingupdate.BibliographicRecord;
import dk.dbc.oss.ns.catalogingupdate.DoubleRecordEntries;
import dk.dbc.oss.ns.catalogingupdate.DoubleRecordEntry;
import dk.dbc.oss.ns.catalogingupdate.ExtraRecordData;
import dk.dbc.oss.ns.catalogingupdate.MessageEntry;
import dk.dbc.oss.ns.catalogingupdate.Messages;
import dk.dbc.oss.ns.catalogingupdate.RecordData;
import dk.dbc.oss.ns.catalogingupdate.UpdateRecordResult;
import dk.dbc.oss.ns.catalogingupdate.UpdateStatusEnum;
import dk.dbc.rawrepo.bindings.BibliographicRecordExtraData;
import dk.dbc.rawrepo.bindings.BibliographicRecordExtraDataMarshaller;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.ws.WebServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

class UpdateServiceHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(UpdateServiceHandler.class);

    private static final String RECORD_SCHEMA = "info:lc/xmlns/marcxchange-v1";
    private static final String RECORD_PACKAGING = "xml";

    private final BibliographicRecordExtraDataMarshaller bibliographicRecordExtraDataMarshaller =
            new BibliographicRecordExtraDataMarshaller();

    private final AtomicInteger errorCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);

    private final String username;
    private final String groupId;
    private final String password;
    private final String template;
    private final String trackingId;
    private final Integer priority;
    private final String provider;
    private final boolean validateOnly;
    private final int errorLimit;
    private final String updateServiceUrl;

    UpdateServiceHandler(String username,
                         String groupId,
                         String password,
                         String template,
                         String trackingId,
                         Integer priority,
                         String provider,
                         boolean validateOnly,
                         int errorLimit,
                         String updateServiceUrl) {
        this.username = username;
        this.groupId = groupId;
        this.password = password;
        this.template = template;
        this.trackingId = trackingId;
        this.priority = priority;
        this.provider = provider;
        this.validateOnly = validateOnly;
        this.errorLimit = errorLimit;
        this.updateServiceUrl = updateServiceUrl;
    }

    void run(MarcReader marcReader) {
        UpdateServiceConnector updateServiceConnector = new UpdateServiceConnector(updateServiceUrl, username, password, validateOnly);

        try {
            final DocumentBuilder dBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();

            final int threadCount = 8;
            AtomicInteger totalCount = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            MarcRecord marcRecord = marcReader.read();
            while (marcRecord != null) {
                totalCount.getAndIncrement();

                if (errorLimit > -1 && errorCount.get() > errorLimit) {
                    throw new RuntimeException("Hit error limit, so aborting");
                }

                final Runnable worker = new UpdateThead(updateServiceConnector, dBuilder, marcRecord);
                executor.execute(worker);

                marcRecord = marcReader.read();

                if (totalCount.get() % 100 == 0 || marcRecord == null) {
                    executor.shutdown();
                    executor.awaitTermination(60, TimeUnit.MINUTES);
                    executor = Executors.newFixedThreadPool(threadCount);
                    if (marcRecord != null) { // Only print the loop message while actually looping
                        LOGGER.info("Processed {} records", totalCount);
                    }
                }
            }
            LOGGER.info("DONE");
            LOGGER.info("Processed a total of {} records", totalCount);
            LOGGER.info("{} with success", successCount);
            LOGGER.info("{} with error", errorCount);
        } catch (ParserConfigurationException e) {
            throw new RuntimeException();
        } catch (InterruptedException e) {
            LOGGER.error("Interrupt exception - aborting.");
            throw new RuntimeException();
        } catch (MarcReaderException e) {
            e.printStackTrace();
        }
    }

    private BibliographicRecord buildRecord(Document content) throws JAXBException {
        final BibliographicRecord bibliographicRecord = new BibliographicRecord();
        bibliographicRecord.setRecordSchema(RECORD_SCHEMA);
        bibliographicRecord.setRecordPacking(RECORD_PACKAGING);

        final ExtraRecordData extraRecordData = new ExtraRecordData();

        if (provider != null || priority != null) {
            final BibliographicRecordExtraData bibliographicRecordExtraData = new BibliographicRecordExtraData();

            if (priority != null) {
                bibliographicRecordExtraData.setPriority(priority);
            }

            if (provider != null) {
                bibliographicRecordExtraData.setProviderName(provider);
            }

            synchronized (bibliographicRecordExtraDataMarshaller) {
                extraRecordData.getContent().add(bibliographicRecordExtraDataMarshaller
                        .toXmlDocument(bibliographicRecordExtraData).getDocumentElement());
            }
        }
        bibliographicRecord.setExtraRecordData(extraRecordData);

        final RecordData recordData = new RecordData();
        recordData.getContent().add(content.getDocumentElement());

        bibliographicRecord.setRecordData(recordData);

        return bibliographicRecord;
    }

    private class UpdateThead implements Runnable {
        private final DocumentBuilder dBuilder;
        private final UpdateServiceConnector updateServiceConnector;
        private final MarcRecord marcRecord;

        UpdateThead(UpdateServiceConnector updateServiceConnector, DocumentBuilder dBuilder, MarcRecord marcRecord) {
            this.updateServiceConnector = updateServiceConnector;
            this.dBuilder = dBuilder;
            this.marcRecord = marcRecord;
        }

        @Override
        public void run() {
            try {
                try {
                    final Document doc;
                    final MarcXchangeV1Writer writer = new MarcXchangeV1Writer();
                    synchronized (dBuilder) {
                        doc = dBuilder.parse(new ByteArrayInputStream(writer.writeRecord(marcRecord, StandardCharsets.UTF_8)));
                    }

                    final BibliographicRecord bibliographicRecord = buildRecord(doc);
                    final UpdateRecordResult result = updateServiceConnector.updateRecord(groupId, template, bibliographicRecord, trackingId);
                    // Error handling. If a record fails in update service we need to know the id of the failed record.
                    if (result.getUpdateStatus() == UpdateStatusEnum.OK) {
                        successCount.getAndIncrement();
                    } else {
                        errorCount.getAndIncrement(); // Check for error count last so that we get the last error message
                        final Optional<DataField> field001 = marcRecord.getField(DataField.class, MarcRecord.hasTag("001"));
                        String recordId = "unknown";
                        String recordAgencyId = "unknown";
                        if (field001.isPresent()) {
                            final DataField dataField001 = field001.get();
                            for (SubField subField : dataField001.getSubFields()) {
                                if (subField.getCode() == 'a') {
                                    recordId = subField.getData();
                                }

                                if (subField.getCode() == 'b') {
                                    recordAgencyId = subField.getData();
                                }
                            }
                        }

                        if (result.getMessages() != null) {
                            final StringBuilder sb = new StringBuilder();
                            sb.append("Error updating '").append(recordId).append(":").append(recordAgencyId).append("'. ");
                            sb.append("Got message: ");

                            final List<String> messageList = new ArrayList<>();
                            final Messages messages = result.getMessages();
                            for (MessageEntry message : messages.getMessageEntry()) {
                                messageList.add(message.getMessage());
                            }
                            sb.append(String.join(", ", messageList));
                            LOGGER.error(sb.toString());
                        } else if (result.getDoubleRecordEntries() != null) {
                            final String doubleRecordKey = result.getDoubleRecordKey();
                            final List<String> messages = getMessages(result);
                            LOGGER.error("Error updating '{}:{}'. Got double record error with key: {} and message(s): {}", recordId, recordAgencyId, doubleRecordKey, String.join(", ", messages));
                        } else {
                            LOGGER.error("Error updating '{}:{}'. Got message: {}", recordId, recordAgencyId, result);
                        }
                    }
                } catch (WebServiceException | NullPointerException | IllegalArgumentException ex) {
                    LOGGER.error("Caught exception from update: {}", ex.toString());
                    errorCount.getAndIncrement();
                    throw new RuntimeException("Aborting");
                } catch (SAXException ex) {
                    LOGGER.error("Could not parse line");
                    errorCount.getAndIncrement();
                    throw new RuntimeException("Aborting");
                } catch (JAXBException e) {
                    LOGGER.error("Could not create extra record data.");
                    errorCount.getAndIncrement();
                    throw new RuntimeException("Aborting");
                }
            } catch (IOException ex) {
                LOGGER.error("Could not read line");
                errorCount.getAndIncrement();
                throw new RuntimeException("Aborting");
            }
        }
    }

    private static List<String> getMessages(UpdateRecordResult result) {
        final List<String> messages = new ArrayList<>();

        final DoubleRecordEntries doubleRecordEntries = result.getDoubleRecordEntries();
        for (DoubleRecordEntry doubleRecordEntry : doubleRecordEntries.getDoubleRecordEntry()) {
            if (!messages.contains(doubleRecordEntry.getMessage())) {
                messages.add(doubleRecordEntry.getMessage());
            }
        }
        return messages;
    }
}
